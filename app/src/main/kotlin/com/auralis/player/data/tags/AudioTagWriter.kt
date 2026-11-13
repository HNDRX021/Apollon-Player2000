package com.auralis.player.data.tags

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.auralis.player.domain.model.ArtworkEdit
import com.auralis.player.domain.model.Edit
import com.auralis.player.domain.model.FileTags
import com.auralis.player.domain.model.TagChanges
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

sealed interface TagWriteResult {
    data object Success : TagWriteResult
    data class Failure(val reason: String) : TagWriteResult
}

class TagReadResult(val tags: FileTags?, val artwork: ByteArray?, val problem: String?)

/**
 * Reads and writes the tags inside audio files with jaudiotagger.
 *
 * The library needs a real [File], but the app only has Storage Access Framework documents, so every
 * operation works on a private copy in the cache folder:
 *  - reading copies the document, reads the tags and deletes the copy;
 *  - writing copies the document, edits the copy, checks that the edited copy can be read again, and only
 *    then streams it back over the original. A second untouched copy is kept until the write-back has been
 *    verified, so a failed write-back restores the original bytes instead of leaving a damaged file.
 *
 * Only the fields named in [TagChanges] are touched; every other tag in the file is left as it was.
 * The operations block on file I/O: call them off the main thread.
 */
@Singleton
class AudioTagWriter @Inject constructor(@ApplicationContext private val context: Context) {

    init {
        // jaudiotagger logs every parsed frame through java.util.logging, which floods logcat on Android.
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    fun isSupported(displayName: String): Boolean = extensionOf(displayName) in SUPPORTED_EXTENSIONS

    fun extensionOf(displayName: String): String = displayName.substringAfterLast('.', "").lowercase()

    fun read(documentUri: Uri, displayName: String): TagReadResult {
        if (!isSupported(displayName)) {
            return TagReadResult(null, null, "Tag editing is not supported for this file format.")
        }
        var temp: File? = null
        return try {
            val copy = copyToTemp(documentUri, displayName)
            temp = copy
            val tag: Tag? = AudioFileIO.read(copy).tag
            if (tag == null) {
                TagReadResult(FileTags(null, null, null, null, null, null, null, null, null, null), null, null)
            } else {
                TagReadResult(tag.toFileTags(), runCatching { tag.firstArtwork?.binaryData }.getOrNull(), null)
            }
        } catch (e: Exception) {
            TagReadResult(null, null, e.message ?: e.javaClass.simpleName)
        } finally {
            temp?.delete()
        }
    }

    fun write(documentUri: Uri, displayName: String, changes: TagChanges): TagWriteResult {
        if (!isSupported(displayName)) {
            return TagWriteResult.Failure("Tag editing is not supported for this file format.")
        }
        val document = DocumentFile.fromSingleUri(context, documentUri)
        if (document == null || !document.exists()) {
            return TagWriteResult.Failure("The file no longer exists.")
        }
        if (!document.canWrite()) {
            return TagWriteResult.Failure(
                "Auralis has no write permission for this file. Remove its music folder in Settings and add it again, " +
                    "choosing \"Allow\" when asked for access.",
            )
        }

        var editedCopy: File? = null
        var untouchedCopy: File? = null
        var keepUntouched = false
        var writeBackStarted = false
        return try {
            val edited = copyToTemp(documentUri, displayName)
            editedCopy = edited
            val untouched = File(edited.parentFile, edited.name + ".orig")
            edited.copyTo(untouched, overwrite = true)
            untouchedCopy = untouched

            val audio = AudioFileIO.read(edited)
            val tag = audio.tagOrCreateAndSetDefault
            applyChanges(tag, changes)
            audio.commit()
            // The edited copy must still parse before it is allowed anywhere near the real file.
            AudioFileIO.read(edited)

            writeBackStarted = true
            val output = context.contentResolver.openOutputStream(documentUri, "wt")
                ?: throw IOException("Could not open the file for writing.")
            output.use { sink -> edited.inputStream().use { source -> source.copyTo(sink) } }
            val onDisk = DocumentFile.fromSingleUri(context, documentUri)?.length() ?: -1L
            if (onDisk != edited.length()) throw IOException("The file has an unexpected size after writing.")
            TagWriteResult.Success
        } catch (e: Exception) {
            var message = e.message ?: e.javaClass.simpleName
            val backup = untouchedCopy
            if (writeBackStarted && backup != null) {
                val restored = runCatching {
                    val sink = context.contentResolver.openOutputStream(documentUri, "wt")
                        ?: throw IOException("Could not open the file for writing.")
                    sink.use { out -> backup.inputStream().use { source -> source.copyTo(out) } }
                }.isSuccess
                message = if (restored) {
                    "Writing to the file failed, so the original was restored. ($message)"
                } else {
                    keepUntouched = true
                    "Writing to the file failed and the original could not be restored automatically. " +
                        "A safe copy of the original was kept at ${backup.absolutePath}. ($message)"
                }
            }
            TagWriteResult.Failure(message)
        } finally {
            editedCopy?.delete()
            if (!keepUntouched) untouchedCopy?.delete()
        }
    }

    // --- Applying changes --------------------------------------------------------

    private fun applyChanges(tag: Tag, changes: TagChanges) {
        setText(tag, FieldKey.TITLE, changes.title)
        setText(tag, FieldKey.ARTIST, changes.artist)
        setText(tag, FieldKey.ALBUM, changes.album)
        setText(tag, FieldKey.ALBUM_ARTIST, changes.albumArtist)
        setText(tag, FieldKey.COMPOSER, changes.composer)
        setText(tag, FieldKey.GENRE, changes.genre)
        setText(tag, FieldKey.COMMENT, changes.comment)
        val year = changes.year
        if (year is Edit.Change) {
            val value = year.value
            if (value == null) deleteField(tag, FieldKey.YEAR) else tag.setField(FieldKey.YEAR, value.toString())
        }
        setNumber(tag, FieldKey.TRACK, FieldKey.TRACK_TOTAL, changes.trackNumber)
        setNumber(tag, FieldKey.DISC_NO, FieldKey.DISC_TOTAL, changes.discNumber)
        when (val artwork = changes.artwork) {
            ArtworkEdit.Keep -> Unit
            ArtworkEdit.Remove -> runCatching { tag.deleteArtworkField() }
            is ArtworkEdit.Replace -> {
                val picture = ArtworkFactory.getNew()
                picture.setBinaryData(artwork.jpegBytes)
                picture.setMimeType("image/jpeg")
                picture.setPictureType(FRONT_COVER)
                runCatching { tag.deleteArtworkField() }
                tag.setField(picture)
            }
        }
    }

    private fun setText(tag: Tag, key: FieldKey, edit: Edit<String>) {
        if (edit !is Edit.Change) return
        val value = edit.value?.trim()
        if (value.isNullOrEmpty()) deleteField(tag, key) else tag.setField(key, value)
    }

    /** Sets a "3" style number while keeping an existing "of 12" total; clearing the number clears the total too. */
    private fun setNumber(tag: Tag, numberKey: FieldKey, totalKey: FieldKey, edit: Edit<Int>) {
        if (edit !is Edit.Change) return
        val number = edit.value
        if (number == null) {
            deleteField(tag, numberKey)
            deleteField(tag, totalKey)
            return
        }
        val total = tag.text(totalKey)
        tag.setField(numberKey, number.toString())
        if (total != null) tag.setField(totalKey, total)
    }

    private fun deleteField(tag: Tag, key: FieldKey) {
        runCatching { tag.deleteField(key) }
    }

    // --- Reading -------------------------------------------------------------------

    private fun Tag.toFileTags() = FileTags(
        title = text(FieldKey.TITLE),
        artist = text(FieldKey.ARTIST),
        album = text(FieldKey.ALBUM),
        albumArtist = text(FieldKey.ALBUM_ARTIST),
        composer = text(FieldKey.COMPOSER),
        genre = text(FieldKey.GENRE),
        year = text(FieldKey.YEAR)?.take(4)?.toIntOrNull(),
        trackNumber = text(FieldKey.TRACK)?.substringBefore('/')?.trim()?.toIntOrNull(),
        discNumber = text(FieldKey.DISC_NO)?.substringBefore('/')?.trim()?.toIntOrNull(),
        comment = text(FieldKey.COMMENT),
    )

    private fun Tag.text(key: FieldKey): String? =
        (try { getFirst(key) } catch (e: Exception) { null })?.trim()?.ifBlank { null }

    // --- Temp files ------------------------------------------------------------------

    private fun copyToTemp(uri: Uri, displayName: String): File {
        val directory = File(context.cacheDir, TEMP_DIR).also { it.mkdirs() }
        val file = File(directory, "${System.nanoTime()}.${extensionOf(displayName)}")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the file.")
            input.use { source -> file.outputStream().use { sink -> source.copyTo(sink) } }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        return file
    }

    private companion object {
        const val TEMP_DIR = "tag-edit"
        const val FRONT_COVER = 3

        /** Formats jaudiotagger can read and write. Anything else (e.g. raw .aac) is reported as unsupported. */
        val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "oga", "opus", "m4a", "m4b", "mp4", "wav", "wma", "aif", "aiff", "aifc", "dsf",
        )
    }
}
