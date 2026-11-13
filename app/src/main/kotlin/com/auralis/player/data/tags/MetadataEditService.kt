package com.auralis.player.data.tags

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.auralis.player.data.artwork.ArtworkStore
import com.auralis.player.data.local.dao.AlbumDao
import com.auralis.player.data.local.dao.ArtistDao
import com.auralis.player.data.local.dao.MusicFolderDao
import com.auralis.player.data.local.dao.SongDao
import com.auralis.player.data.local.entity.SongEntity
import com.auralis.player.data.scanner.LibraryScanner
import com.auralis.player.di.IoDispatcher
import com.auralis.player.domain.model.ArtworkEdit
import com.auralis.player.domain.model.Edit
import com.auralis.player.domain.model.MetadataEditOutcome
import com.auralis.player.domain.model.MetadataEditRequest
import com.auralis.player.domain.model.SongEditFailure
import com.auralis.player.domain.model.SongTagSnapshot
import com.auralis.player.domain.model.TagChanges
import com.auralis.player.domain.util.albumArtistIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Edit File": writes tag changes into the real audio files, then brings the library database in line.
 *
 * The database is only updated for files that were written successfully, so the library never claims a
 * change the file does not have. Song rows are updated in place ([SongDao.update], never a REPLACE), which
 * keeps song ids, favorites, play counts, playlist entries and the playback queue intact. Albums are
 * renamed in place when a whole album is edited, and an album that loses its last track is deleted so no
 * empty duplicate is left behind.
 */
@Singleton
class MetadataEditService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val musicFolderDao: MusicFolderDao,
    private val libraryScanner: LibraryScanner,
    private val artworkStore: ArtworkStore,
    private val tagWriter: AudioTagWriter,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** One edit at a time: two overlapping edits would race on the same files and album rows. */
    private val editMutex = Mutex()

    private suspend fun documentUri(song: SongEntity): Uri? {
        val folder = musicFolderDao.findById(song.folderId) ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(Uri.parse(folder.treeUri), song.documentId)
    }

    /** Reads what the editor shows for [songId]: the tags inside the file, plus whether the file may be edited. */
    suspend fun readTags(songId: Long): SongTagSnapshot? = withContext(io) {
        val song = songDao.findEntityById(songId) ?: return@withContext null
        val extension = tagWriter.extensionOf(song.displayName)
        if (!tagWriter.isSupported(song.displayName)) {
            return@withContext SongTagSnapshot(
                songId, null, null,
                notEditableReason = "Editing tags is not supported for .$extension files.",
                readProblem = null,
            )
        }
        val uri = documentUri(song)
            ?: return@withContext SongTagSnapshot(
                songId, null, null,
                notEditableReason = "The music folder of this file is no longer in the library.",
                readProblem = null,
            )
        val canWrite = DocumentFile.fromSingleUri(context, uri)?.canWrite() == true
        val read = tagWriter.read(uri, song.displayName)
        val reason = when {
            !canWrite -> "Auralis has no write permission for this file. Remove its music folder in Settings and add it again, " +
                "choosing \"Allow\" when asked for access."
            read.tags == null -> "The tags inside this file could not be read, so it cannot be edited safely."
            else -> null
        }
        SongTagSnapshot(songId, read.tags, read.artwork, reason, read.problem)
    }

    suspend fun apply(request: MetadataEditRequest): MetadataEditOutcome = withContext(io) {
        editMutex.withLock {
            if (libraryScanner.progress.value.isScanning) {
                return@withLock MetadataEditOutcome(
                    emptyList(),
                    request.songs.map {
                        SongEditFailure(it.songId, titleOf(it.songId), "A library scan is running. Try again when it has finished.")
                    },
                )
            }

            val updated = ArrayList<Long>()
            val failures = ArrayList<SongEditFailure>()
            for (edit in request.songs) {
                val entity = songDao.findEntityById(edit.songId)
                if (entity == null) {
                    failures += SongEditFailure(edit.songId, "Unknown song", "This song is no longer in the library.")
                    continue
                }
                val uri = documentUri(entity)
                if (uri == null) {
                    failures += SongEditFailure(entity.id, entity.title, "Its music folder is no longer in the library.")
                    continue
                }
                when (val written = tagWriter.write(uri, entity.displayName, edit.changes)) {
                    is TagWriteResult.Failure -> failures += SongEditFailure(entity.id, entity.title, written.reason)
                    TagWriteResult.Success -> {
                        try {
                            syncLibrary(request.albumId, entity.id, edit.changes, uri)
                            updated += entity.id
                        } catch (e: Exception) {
                            failures += SongEditFailure(
                                entity.id,
                                entity.title,
                                "The file was saved, but the library could not be updated (${e.message ?: e.javaClass.simpleName}). " +
                                    "Refresh the library to pick the change up.",
                            )
                        }
                    }
                }
            }
            MetadataEditOutcome(updated, failures)
        }
    }

    private suspend fun titleOf(songId: Long): String = songDao.findEntityById(songId)?.title ?: "Unknown song"

    /** Mirrors a successful file write into the song, album and artist rows. */
    private suspend fun syncLibrary(wholeAlbumId: Long?, songId: Long, changes: TagChanges, uri: Uri) {
        val before = songDao.findEntityById(songId) ?: return
        val oldAlbum = before.albumId?.let { albumDao.findById(it) }
        val oldAlbumArtistName = oldAlbum?.albumArtistId?.let { artistDao.findById(it)?.name }

        val title = changes.title.resolve(before.title) ?: before.title
        val composer = changes.composer.resolve(before.composer)
        val year = changes.year.resolveValue(before.year)
        val trackNumber = changes.trackNumber.resolveValue(before.trackNumber)
        val discNumber = changes.discNumber.resolveValue(before.discNumber) ?: 1

        val artistId = when (val e = changes.artist) {
            Edit.Keep -> before.artistId
            is Edit.Change -> e.value?.trim()?.ifBlank { null }?.let { libraryScanner.findOrCreateArtist(it) }
        }
        val genreId = when (val e = changes.genre) {
            Edit.Keep -> before.genreId
            is Edit.Change -> e.value?.trim()?.ifBlank { null }?.let { libraryScanner.findOrCreateGenre(it) }
        }

        // Albums are identified by (title, album artist), exactly as the scanner does it.
        val newAlbumTitle = changes.album.resolve(oldAlbum?.title)
        val newAlbumArtistName = changes.albumArtist.resolve(oldAlbumArtistName)
        val renameInPlace = wholeAlbumId != null && wholeAlbumId == before.albumId
        val newAlbumId: Long? = if (newAlbumTitle == null) {
            null
        } else {
            val albumArtistId = libraryScanner.findOrCreateArtist(albumArtistIdentity(newAlbumArtistName))
            val existing = albumDao.findByTitleAndArtist(newAlbumTitle, albumArtistId)
            when {
                existing != null -> existing.id
                renameInPlace && oldAlbum != null -> {
                    albumDao.updateIdentity(oldAlbum.id, newAlbumTitle, albumArtistId)
                    oldAlbum.id
                }
                else -> libraryScanner.findOrCreateAlbum(newAlbumTitle, albumArtistId)
            }
        }

        // Read the row again right before updating it, so a favorite toggled while the file was being
        // written is not overwritten with the old value.
        val latest = songDao.findEntityById(songId) ?: return
        val file = DocumentFile.fromSingleUri(context, uri)
        songDao.update(
            latest.copy(
                title = title,
                titleSortKey = title.lowercase(),
                artistId = artistId,
                albumId = newAlbumId,
                genreId = genreId,
                composer = composer,
                year = year,
                trackNumber = trackNumber,
                discNumber = discNumber,
                // New size / modified time, so the next library refresh sees the file as unchanged
                // instead of re-importing it.
                fileSize = file?.length()?.takeIf { it > 0 } ?: latest.fileSize,
                lastModified = file?.lastModified()?.takeIf { it > 0 } ?: latest.lastModified,
                dateModified = System.currentTimeMillis(),
            ),
        )

        if (newAlbumId != null) {
            when (val artwork = changes.artwork) {
                is ArtworkEdit.Replace -> artworkStore.save(artwork.jpegBytes)?.let { albumDao.setArtwork(newAlbumId, it) }
                ArtworkEdit.Remove -> albumDao.setArtwork(newAlbumId, null)
                ArtworkEdit.Keep -> {
                    // A song that moved to a new album row keeps showing the cover it had.
                    val previousCover = oldAlbum?.artworkUri
                    if (oldAlbum != null && previousCover != null && oldAlbum.id != newAlbumId) {
                        albumDao.setArtworkIfMissing(newAlbumId, previousCover)
                    }
                }
            }
            albumDao.updateCounts(newAlbumId)
            if (changes.year is Edit.Change) {
                // The release date shown on the album page follows an explicit year edit: always for a
                // whole-album edit, and for a song edit only when that song is the album's only track.
                val onlyTrack = songDao.countAvailableInAlbum(newAlbumId) <= 1
                if (renameInPlace || (wholeAlbumId == null && onlyTrack)) {
                    albumDao.setReleaseDate(newAlbumId, year?.toString() ?: "")
                }
            }
        }
        if (oldAlbum != null && oldAlbum.id != newAlbumId) {
            albumDao.updateCounts(oldAlbum.id)
            albumDao.deleteIfUnused(oldAlbum.id)
        }
    }

    private fun Edit<String>.resolve(current: String?): String? = when (this) {
        Edit.Keep -> current
        is Edit.Change -> value?.trim()?.ifBlank { null }
    }

    private fun <T> Edit<T>.resolveValue(current: T?): T? = when (this) {
        Edit.Keep -> current
        is Edit.Change -> value
    }
}
