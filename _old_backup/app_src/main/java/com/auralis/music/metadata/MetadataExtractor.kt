package com.auralis.music.metadata

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import com.auralis.music.data.SongEntity
import java.io.File
import java.security.MessageDigest

object MetadataExtractor {
    private const val MAX_ARTWORK_BYTES = 1_000_000

    fun extract(
        contentResolver: ContentResolver,
        uri: Uri,
        folderId: Long?,
        fileName: String,
        fileSize: Long,
        modifiedAt: Long,
        artworkDir: File
    ): SongEntity? {
        val retriever = MediaMetadataRetriever()
        val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: return null
        return try {
            retriever.setDataSource(descriptor.fileDescriptor)
            val rawTitle = retriever.meta(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val rawArtist = retriever.meta(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.meta(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val albumArtist = retriever.meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                .ifBlank { rawArtist }
            val composer = retriever.meta(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            val genre = retriever.meta(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val date = retriever.meta(MediaMetadataRetriever.METADATA_KEY_DATE)
            val duration = retriever.meta(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0L
            val bitrate = retriever.meta(MediaMetadataRetriever.METADATA_KEY_BITRATE).toIntOrNull()
            val sampleRate = retriever.meta(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE).toIntOrNull()
            val mimeType = retriever.meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).ifBlank { guessMime(fileName) }
            val track = parseNumber(retriever.meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
            val totalTracks = parseTotal(retriever.meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
            val disc = parseNumber(retriever.meta(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER))
            val totalDiscs = parseTotal(retriever.meta(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER))
            val embedded = retriever.embeddedPicture?.takeIf { it.size <= MAX_ARTWORK_BYTES }

            val safeTitle = rawTitle.ifBlank {
                fileName.substringBeforeLast('.', fileName)
                    .replace(Regex("^\\d+\\s*[-._]\\s*"), "")
            }
            val safeArtist = rawArtist.ifBlank { "Unknown Artist" }
            val safeAlbum = album.ifBlank { "Unknown Album" }
            val fingerprint = fingerprint(contentResolver, uri, fileSize, duration)
            val artworkPath = embedded?.let { saveArtwork(it, fingerprint, artworkDir) }

            SongEntity(
                sourceUri = uri.toString(),
                folderId = folderId,
                title = safeTitle,
                artist = safeArtist,
                album = safeAlbum,
                albumArtist = albumArtist.ifBlank { safeArtist },
                composer = composer,
                genre = genre,
                year = parseYear(date),
                trackNumber = track,
                totalTracks = totalTracks,
                discNumber = disc,
                totalDiscs = totalDiscs,
                durationMs = duration,
                bitrate = bitrate,
                sampleRate = sampleRate,
                mimeType = mimeType,
                fileName = fileName,
                fileSize = fileSize,
                modifiedAt = modifiedAt,
                artworkPath = artworkPath,
                fingerprint = fingerprint
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { descriptor.close() }
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.meta(key: Int): String =
        runCatching { extractMetadata(key).orEmpty() }.getOrDefault("")

    private fun parseNumber(value: String): Int? = value.substringBefore('/').trim().toIntOrNull()
    private fun parseTotal(value: String): Int? = value.substringAfter('/', "").trim().toIntOrNull()
    private fun parseYear(value: String): Int? = Regex("(19|20)\\d{2}").find(value)?.value?.toIntOrNull()

    private fun guessMime(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "m4a", "aac" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        else -> "audio/*"
    }

    private fun fingerprint(resolver: ContentResolver, uri: Uri, size: Long, duration: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("$size:$duration".toByteArray())
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(32 * 1024)
            val read = input.read(buffer)
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun saveArtwork(bytes: ByteArray, fingerprint: String, dir: File): String? = try {
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$fingerprint.jpg")
        if (!file.exists()) file.writeBytes(bytes)
        file.absolutePath
    } catch (_: Exception) {
        null
    }
}
