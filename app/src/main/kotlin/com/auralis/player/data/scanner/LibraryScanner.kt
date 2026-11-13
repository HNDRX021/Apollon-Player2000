package com.auralis.player.data.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.auralis.player.data.artwork.ArtworkStore
import com.auralis.player.data.local.dao.AlbumDao
import com.auralis.player.data.local.dao.DuplicateFingerprintDao
import com.auralis.player.data.local.dao.ArtistDao
import com.auralis.player.data.local.dao.GenreDao
import com.auralis.player.data.local.dao.MusicFolderDao
import com.auralis.player.data.local.dao.SongDao
import com.auralis.player.data.local.dao.SongFingerprintRow
import com.auralis.player.data.local.entity.AlbumEntity
import com.auralis.player.data.local.entity.DuplicateFingerprintEntity
import com.auralis.player.data.local.entity.ArtistEntity
import com.auralis.player.data.local.entity.GenreEntity
import com.auralis.player.data.local.entity.MusicFolderEntity
import com.auralis.player.data.local.entity.SongEntity
import com.auralis.player.di.IoDispatcher
import com.auralis.player.domain.model.ScanOutcome
import com.auralis.player.domain.model.ScanProgress
import com.auralis.player.domain.util.DuplicateVerdict
import com.auralis.player.domain.util.ExistingTrack
import com.auralis.player.domain.util.albumArtistIdentity
import com.auralis.player.domain.util.TrackFingerprint
import com.auralis.player.domain.util.TrackIdentity
import com.auralis.player.domain.util.ScanProgressThrottle
import com.auralis.player.settings.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class ScanResult(
    val folderId: Long,
    val imported: Int,
    val updated: Int,
    val unchanged: Int,
    val skippedDuplicates: Int,
    val markedMissing: Boolean,
    val failed: Boolean = false,
)

private data class PreparedScanDocument(
    val discovered: DiscoveredDocument,
    val existingRow: SongFingerprintRow?,
    val metadata: ExtractedMetadata?,
)

@Singleton
class LibraryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val treeWalker: DocumentTreeWalker,
    private val metadataExtractor: AudioMetadataExtractor,
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val duplicateFingerprintDao: DuplicateFingerprintDao,
    private val genreDao: GenreDao,
    private val musicFolderDao: MusicFolderDao,
    private val settingsDataStore: SettingsDataStore,
    private val artworkStore: ArtworkStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    // Scans are serialized: two overlapping scans would race on the
    // find-or-create artist/album/genre lookups and on the same song rows.
    private val scanMutex = Mutex()

    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    private val _outcome = MutableStateFlow<ScanOutcome?>(null)

    /** What the most recent finished (not cancelled) run found; null until one finishes. */
    val outcome: StateFlow<ScanOutcome?> = _outcome.asStateFlow()

    suspend fun scanAllEnabledFolders(): List<ScanResult> = scanFolders(musicFolderDao.getEnabled())

    suspend fun scanFolder(folder: MusicFolderEntity): ScanResult = scanFolders(listOf(folder)).first()

    /**
     * Scans [folders] one after another as a single run: [progress] stays "scanning" the whole time
     * (no flicker between folders) and one [outcome] is published at the end. A folder that cannot be
     * read (missing, or its access grant was revoked) is reported as failed and the remaining folders
     * are still scanned; one bad folder never crashes the app or hides the rest of the library, and
     * nothing already indexed is dropped because a folder could not be read.
     */
    suspend fun scanFolders(folders: List<MusicFolderEntity>): List<ScanResult> = withContext(ioDispatcher) {
        if (folders.isEmpty()) return@withContext emptyList()
        scanMutex.withLock {
            val results = ArrayList<ScanResult>(folders.size)
            var failedFolderId: Long? = null
            var finished = false
            try {
                for (folder in folders) {
                    _progress.value = ScanProgress(
                        isScanning = true,
                        folderId = folder.id,
                        lastScanFailed = failedFolderId != null,
                        failedFolderId = failedFolderId,
                    )
                    val result = try {
                        scanFolderLocked(folder)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ScanResult(folder.id, 0, 0, 0, 0, markedMissing = false, failed = true)
                    }
                    if (result.failed) failedFolderId = folder.id
                    results += result
                }
                finished = true
            } finally {
                _progress.value = ScanProgress(
                    isScanning = false,
                    lastScanFailed = failedFolderId != null,
                    failedFolderId = failedFolderId,
                )
            }
            if (finished) {
                _outcome.value = ScanOutcome(
                    id = System.nanoTime(),
                    newSongs = results.sumOf { it.imported },
                    updatedSongs = results.sumOf { it.updated },
                    foldersScanned = results.count { !it.failed },
                    failedFolderIds = results.filter { it.failed }.map { it.folderId },
                )
            }
            results
        }
    }

    /** Re-extracts and updates one existing song while preserving its row identity and user state. */
    suspend fun refreshSongMetadata(songId: Long): Boolean = withContext(ioDispatcher) {
        scanMutex.withLock {
            val existing = songDao.findEntityById(songId) ?: return@withLock false
            val folder = musicFolderDao.findById(existing.folderId) ?: return@withLock false
            val treeUri = Uri.parse(folder.treeUri)
            val metadata = metadataExtractor.extract(treeUri, existing.documentId, existing.title) ?: return@withLock false
            val document = DiscoveredDocument(
                documentId = existing.documentId,
                displayName = existing.displayName,
                mimeType = existing.mimeType,
                size = existing.fileSize,
                lastModified = existing.lastModified,
            )
            importOrUpdate(
                folder = folder,
                discovered = document,
                fingerprint = TrackFingerprint(existing.documentId, existing.fileSize, existing.lastModified, existing.contentFingerprint),
                existingSongId = existing.id,
                metadata = metadata,
            )
        }
    }

    private suspend fun scanFolderLocked(folder: MusicFolderEntity): ScanResult {
        val treeUri = Uri.parse(folder.treeUri)
        // A deleted/unmounted folder or a revoked grant must surface as a failed scan (and a clear
        // message), not as a successful scan of an empty folder.
        val root = DocumentFile.fromTreeUri(context, treeUri)
        if (root == null || !root.exists() || !root.canRead()) {
            throw IllegalStateException("Music folder is missing or not readable: ${folder.displayName}")
        }
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val appSettings = settingsDataStore.settings.first()
        val deepScan = appSettings.duplicateHandlingMode == com.auralis.player.settings.DuplicateHandlingMode.SKIP_IDENTICAL_CONTENT

        var imported = 0
        var updated = 0
        var unchanged = 0
        var skippedDuplicates = 0
        var processed = 0
        val seenDocumentIds = HashSet<String>()
        val artworkBackfillAttempted = HashSet<Long>()
        val progressThrottle = ScanProgressThrottle()
        val metadataSemaphore = Semaphore(METADATA_EXTRACTION_CONCURRENCY)

        val walk = treeWalker.walk(
            treeUri = treeUri,
            rootDocumentId = rootDocumentId,
            recursive = folder.isRecursive,
            ignoredNames = appSettings.ignoredFolderNames,
            respectNoMedia = appSettings.respectNoMedia,
            enabledAudioFormats = appSettings.enabledAudioFormats,
        )
        for (batch in walk.chunked(SCAN_BATCH_SIZE)) {
            // Read the existing fingerprints in source order, then extract
            // metadata concurrently. All identity checks and Room writes stay
            // ordered below, so identical files in one batch still dedupe.
            val existingByDocumentId = songDao
                .findFingerprintsByDocumentIds(batch.map { it.documentId })
                .associateBy { it.documentId }
            val fingerprints = batch.map { discovered ->
                discovered to existingByDocumentId[discovered.documentId]
            }
            val prepared = coroutineScope {
                fingerprints.map { (discovered, existingRow) ->
                    async(ioDispatcher) {
                        val changed = existingRow == null ||
                            existingRow.fileSize != discovered.size ||
                            existingRow.lastModified != discovered.lastModified
                        val metadata = if (changed) {
                            metadataSemaphore.withPermit {
                                val fallbackTitle = discovered.displayName.substringBeforeLast('.', discovered.displayName)
                                metadataExtractor.extract(treeUri, discovered.documentId, fallbackTitle)
                            }
                        } else null
                        PreparedScanDocument(discovered, existingRow, metadata)
                    }
                }.awaitAll()
            }

            for (candidate in prepared) {
            val discovered = candidate.discovered
            val existingRow = candidate.existingRow
            seenDocumentIds += discovered.documentId
            processed++
            if (progressThrottle.shouldEmit(processed, System.nanoTime() / 1_000_000L)) {
                _progress.value = ScanProgress(
                    isScanning = true,
                    processed = processed,
                    currentName = discovered.displayName,
                    folderId = folder.id,
                )
            }

            val changedOnDisk = existingRow == null ||
                existingRow.fileSize != discovered.size ||
                existingRow.lastModified != discovered.lastModified

            // Hash only when it can matter: a new or modified file. Unchanged
            // files are skipped on size + modified time alone, so a rescan of
            // a big library with deep scan on doesn't re-read every file.
            val cachedDuplicate = if (deepScan && existingRow == null) {
                duplicateFingerprintDao.find(folder.id, discovered.documentId)
            } else {
                null
            }
            val contentHash = if (deepScan && changedOnDisk) {
                cachedDuplicate
                    ?.takeIf { it.fileSize == discovered.size && it.lastModified == discovered.lastModified }
                    ?.contentHash
                    ?: hashDocument(treeUri, discovered.documentId)
            } else null

            val fingerprint = TrackFingerprint(
                documentId = discovered.documentId,
                fileSize = discovered.size,
                lastModified = discovered.lastModified,
                contentHash = contentHash,
            )

            val verdict = TrackIdentity.evaluate(
                candidate = fingerprint,
                findByDocumentId = { existingRow?.toExistingTrack() },
                findByContentHash = { hash ->
                    songDao.findFingerprintByContentHash(hash)?.toExistingTrack()
                        ?: duplicateFingerprintDao.findByHash(hash)?.let {
                            ExistingTrack(
                                songId = -1L,
                                fingerprint = TrackFingerprint(it.documentId, it.fileSize, it.lastModified, it.contentHash),
                            )
                        }
                },
            )

            when (verdict) {
                is DuplicateVerdict.SkipUnchanged -> {
                    unchanged++
                    // Existing albums with no cover or no recorded release date used to stay that way
                    // forever because unchanged files bypass metadata reads. Read one file per album,
                    // once per scan. A blank release date records "looked, none found" so it is not retried.
                    val albumId = existingRow?.albumId
                    if (albumId != null && artworkBackfillAttempted.add(albumId)) {
                        val album = albumDao.findById(albumId)
                        if (album != null && (album.artworkUri == null || album.releaseDate == null)) {
                            val extracted = metadataExtractor.extract(treeUri, discovered.documentId, discovered.displayName)
                            saveArtworkIfNeeded(albumId, extracted?.artworkBytes)
                            if (album.releaseDate == null && extracted != null) {
                                albumDao.setReleaseDateIfBetter(albumId, extracted.releaseDate ?: "")
                            }
                        }
                    }
                }
                is DuplicateVerdict.SkipDuplicateContent -> {
                    skippedDuplicates++
                    if (contentHash != null) {
                        duplicateFingerprintDao.upsert(
                            DuplicateFingerprintEntity(folder.id, discovered.documentId, discovered.size, discovered.lastModified, contentHash),
                        )
                    }
                }
                is DuplicateVerdict.Import -> {
                    if (importOrUpdate(folder, discovered, fingerprint, existingSongId = null, metadata = candidate.metadata)) imported++
                }
                is DuplicateVerdict.UpdateMetadata -> {
                    if (importOrUpdate(folder, discovered, fingerprint, existingSongId = verdict.songId, metadata = candidate.metadata)) updated++
                }
            }
            }
            // Publish the end of each batch even when the time/file threshold
            // was not reached, so small final batches are reflected promptly.
            _progress.value = ScanProgress(
                isScanning = true,
                processed = processed,
                currentName = batch.lastOrNull()?.displayName,
                folderId = folder.id,
            )
        }

        // If nothing at all was seen, the folder was probably unreadable
        // rather than genuinely emptied; don't flag the whole library missing.
        val markedMissing = if (seenDocumentIds.isNotEmpty()) {
            markMissingSongs(folder.id, seenDocumentIds)
            true
        } else {
            false
        }
        musicFolderDao.markScanned(folder.id, System.currentTimeMillis())

        return ScanResult(
            folderId = folder.id,
            imported = imported,
            updated = updated,
            unchanged = unchanged,
            skippedDuplicates = skippedDuplicates,
            markedMissing = markedMissing,
        )
    }

    /**
     * Compares the DB's available rows against what the walk saw, and flags
     * the difference in small batches (SQLite caps bound parameters per
     * statement, so one giant IN (...) would fail on large libraries).
     */
    private suspend fun markMissingSongs(folderId: Long, seen: Set<String>) {
        val missing = songDao.getAvailableDocumentIds(folderId).filter { it !in seen }
        missing.chunked(500).forEach { batch -> songDao.markMissingByIds(folderId, batch) }
    }

    private suspend fun importOrUpdate(
        folder: MusicFolderEntity,
        discovered: DiscoveredDocument,
        fingerprint: TrackFingerprint,
        existingSongId: Long?,
        metadata: ExtractedMetadata?,
    ): Boolean {
        metadata ?: return false

        val artistId = metadata.artistName?.let { findOrCreateArtist(it) }
        val albumArtistId = metadata.albumTitle?.let {
            findOrCreateArtist(albumArtistIdentity(metadata.albumArtistName))
        }
        val albumId = metadata.albumTitle?.let { findOrCreateAlbum(it, albumArtistId) }
        val genreId = metadata.genreName?.let { findOrCreateGenre(it) }
        val now = System.currentTimeMillis()

        if (existingSongId != null) {
            // Metadata refresh on a file already in the library: fetch the
            // current row so favorite status, play count, and playback
            // history stay attached, then issue a real UPDATE (see the
            // comment on SongDao.update for why this must not be upsert()).
            val existing = songDao.findByDocumentId(fingerprint.documentId) ?: return false
            val previousAlbumId = existing.albumId
            val refreshed = existing.copy(
                filePath = discovered.displayName,
                displayName = discovered.displayName,
                fileSize = fingerprint.fileSize,
                lastModified = fingerprint.lastModified,
                contentFingerprint = fingerprint.contentHash ?: existing.contentFingerprint,
                title = metadata.title,
                titleSortKey = metadata.title.lowercase(),
                artistId = artistId,
                albumId = albumId,
                genreId = genreId,
                composer = metadata.composer,
                year = metadata.year,
                trackNumber = metadata.trackNumber,
                totalTracks = metadata.totalTracks,
                discNumber = metadata.discNumber,
                totalDiscs = metadata.totalDiscs,
                durationMs = metadata.durationMs,
                bitrateKbps = metadata.bitrateKbps,
                sampleRateHz = metadata.sampleRateHz,
                mimeType = discovered.mimeType,
                dateModified = now,
                isAvailable = true,
            )
            songDao.update(refreshed)
            // A retag can move the song to a different album; refresh the old one too.
            if (previousAlbumId != null && previousAlbumId != albumId) albumDao.updateCounts(previousAlbumId)
        } else {
            val song = SongEntity(
                documentId = fingerprint.documentId,
                folderId = folder.id,
                filePath = discovered.displayName,
                displayName = discovered.displayName,
                fileSize = fingerprint.fileSize,
                lastModified = fingerprint.lastModified,
                contentFingerprint = fingerprint.contentHash,
                title = metadata.title,
                titleSortKey = metadata.title.lowercase(),
                artistId = artistId,
                albumId = albumId,
                genreId = genreId,
                composer = metadata.composer,
                year = metadata.year,
                trackNumber = metadata.trackNumber,
                totalTracks = metadata.totalTracks,
                discNumber = metadata.discNumber,
                totalDiscs = metadata.totalDiscs,
                durationMs = metadata.durationMs,
                bitrateKbps = metadata.bitrateKbps,
                sampleRateHz = metadata.sampleRateHz,
                mimeType = discovered.mimeType,
                dateAdded = now,
                dateModified = now,
                isAvailable = true,
            )
            songDao.upsert(song)
        }

        if (albumId != null) {
            albumDao.updateCounts(albumId)
            metadata.releaseDate?.let { albumDao.setReleaseDateIfBetter(albumId, it) }
            val albumArtwork = saveArtworkIfNeeded(albumId, metadata.artworkBytes)
                ?: albumDao.findById(albumId)?.artworkUri
            if (albumArtwork != null && albumArtistId != null) {
                artistDao.setArtworkIfMissing(albumArtistId, albumArtwork)
            }
        }
        return true
    }

    private suspend fun saveArtworkIfNeeded(albumId: Long, bytes: ByteArray?): String? {
        val existing = albumDao.findById(albumId)?.artworkUri
        if (existing != null) return existing
        if (bytes == null) return null
        val path = artworkStore.save(bytes) ?: return null
        albumDao.setArtworkIfMissing(albumId, path)
        return albumDao.findById(albumId)?.artworkUri ?: path
    }

    internal suspend fun findOrCreateArtist(name: String): Long {
        artistDao.findByName(name)?.let { return it.id }
        val inserted = artistDao.insert(ArtistEntity(name = name, sortName = name.lowercase()))
        return if (inserted > 0) inserted else artistDao.findByName(name)?.id ?: inserted
    }

    internal suspend fun findOrCreateGenre(name: String): Long {
        genreDao.findByName(name)?.let { return it.id }
        val inserted = genreDao.insert(GenreEntity(name = name))
        return if (inserted > 0) inserted else genreDao.findByName(name)?.id ?: inserted
    }

    internal suspend fun findOrCreateAlbum(title: String, albumArtistId: Long?): Long {
        albumDao.findByTitleAndArtist(title, albumArtistId)?.let { return it.id }
        val inserted = albumDao.insert(
            AlbumEntity(title = title, albumArtistId = albumArtistId, dateAdded = System.currentTimeMillis()),
        )
        return if (inserted > 0) inserted else albumDao.findByTitleAndArtist(title, albumArtistId)?.id ?: inserted
    }

    private fun hashDocument(treeUri: Uri, documentId: String): String? {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return try {
            context.contentResolver.openInputStream(documentUri)?.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun SongFingerprintRow.toExistingTrack() = ExistingTrack(
        songId = id,
        fingerprint = TrackFingerprint(
            documentId = documentId,
            fileSize = fileSize,
            lastModified = lastModified,
            contentHash = contentFingerprint,
        ),
    )

    private companion object {
        const val SCAN_BATCH_SIZE = 32
        const val METADATA_EXTRACTION_CONCURRENCY = 4
    }
}
