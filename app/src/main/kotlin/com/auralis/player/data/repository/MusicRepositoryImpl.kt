package com.auralis.player.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.auralis.player.data.local.dao.AlbumDao
import com.auralis.player.data.local.dao.ArtistDao
import com.auralis.player.data.local.dao.DuplicateFingerprintDao
import com.auralis.player.data.local.dao.MusicFolderDao
import com.auralis.player.data.local.dao.PlaybackHistoryDao
import com.auralis.player.data.local.dao.PlaylistDao
import com.auralis.player.data.local.dao.SongDao
import com.auralis.player.data.local.entity.MusicFolderEntity
import com.auralis.player.data.local.entity.PlaylistEntity
import com.auralis.player.data.scanner.LibraryScanner
import com.auralis.player.data.scanner.LibraryScanWorker
import com.auralis.player.domain.model.Album
import com.auralis.player.domain.model.AlbumRecentPlays
import com.auralis.player.domain.model.Artist
import com.auralis.player.domain.model.LibraryStats
import com.auralis.player.domain.model.MusicFolder
import com.auralis.player.domain.model.Playlist
import com.auralis.player.domain.model.PlaylistEntry
import com.auralis.player.domain.model.ScanOutcome
import com.auralis.player.domain.model.ScanProgress
import com.auralis.player.domain.model.Song
import com.auralis.player.domain.model.toDomain
import com.auralis.player.domain.repository.MusicRepository
import com.auralis.player.domain.util.albumReleaseOrder
import com.auralis.player.domain.util.albumTrackOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val musicFolderDao: MusicFolderDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val playlistDao: PlaylistDao,
    private val duplicateFingerprintDao: DuplicateFingerprintDao,
    private val libraryScanner: LibraryScanner,
    private val genreDao: com.auralis.player.data.local.dao.GenreDao,
    private val metadataEditService: com.auralis.player.data.tags.MetadataEditService,
    private val artworkStore: com.auralis.player.data.artwork.ArtworkStore,
    @ApplicationContext private val context: Context,
) : MusicRepository {

    override val scanProgress: StateFlow<ScanProgress> = libraryScanner.progress
    override val scanOutcome: StateFlow<ScanOutcome?> = libraryScanner.outcome

    // Every list flow below converts its database rows to domain models (and some sort them) with
    // flowOn(Dispatchers.Default): a collector from Compose runs on the main thread, so without it a
    // 10,000-song library was mapped and sorted on the UI thread each time the table changed.

    override fun observeRecentlyPlayedSongs(limit: Int): Flow<List<Song>> =
        songDao.observeRecentlyPlayed(limit).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override fun observeRecentlyAddedSongs(limit: Int): Flow<List<Song>> =
        songDao.observeRecentlyAdded(limit).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override fun observeFavoriteSongs(limit: Int): Flow<List<Song>> =
        songDao.observeFavorites(limit).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override fun observeMostPlayedSongs(limit: Int): Flow<List<Song>> =
        songDao.observeMostPlayed(limit).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override suspend fun setFavorite(songId: Long, isFavorite: Boolean) {
        songDao.setFavorite(songId, isFavorite, favoritedAt = if (isFavorite) System.currentTimeMillis() else null)
    }

    override suspend fun setAlbumFavorite(albumId: Long, isFavorite: Boolean) {
        albumDao.setFavorite(albumId, isFavorite)
    }

    override suspend fun setArtistFavorite(artistId: Long, isFavorite: Boolean) {
        artistDao.setFavorite(artistId, isFavorite)
    }

    override suspend fun addFolderAndScan(treeUri: String, displayName: String) {
        val folderId = musicFolderDao.insert(
            MusicFolderEntity(
                treeUri = treeUri,
                displayName = displayName,
                addedAt = System.currentTimeMillis(),
            ),
        )
        // insert() uses OnConflictStrategy.IGNORE (see MusicFolderDao) — if
        // the user re-picks a folder already saved, folderId comes back -1
        // and there is nothing new to scan.
        if (folderId <= 0) return

        val folder = MusicFolderEntity(
            id = folderId,
            treeUri = treeUri,
            displayName = displayName,
            addedAt = System.currentTimeMillis(),
        )
        LibraryScanWorker.enqueue(context, folder.id)
    }

    // Folder id -> its SAF tree URI. resolvePlayableUri() runs once per queued song, i.e. thousands of times
    // when a whole Library list is queued, and used to query the database for every single one of them.
    private val treeUriCache = ConcurrentHashMap<Long, Uri>()

    private suspend fun treeUriOf(folderId: Long): Uri? {
        treeUriCache[folderId]?.let { return it }
        val folder = musicFolderDao.findById(folderId) ?: return null
        return Uri.parse(folder.treeUri).also { treeUriCache[folderId] = it }
    }

    override suspend fun resolvePlayableUri(song: Song): Uri? {
        val treeUri = treeUriOf(song.folderId) ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, song.documentId)
    }

    override suspend fun resolveParentFolderUri(song: Song): Uri? {
        val treeUri = treeUriOf(song.folderId) ?: return null
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentId = song.documentId.substringBeforeLast('/', rootId).ifBlank { rootId }
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
    }

    override suspend fun refreshSongMetadata(songId: Long): Boolean = libraryScanner.refreshSongMetadata(songId)

    override suspend fun getSongsByIds(ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        // Chunked: SQLite limits bound parameters per statement.
        val byId = ids.distinct().chunked(500)
            .flatMap { chunk -> songDao.findWithMetadataByIds(chunk) }
            .associateBy { it.song.id }
        return ids.mapNotNull { id -> byId[id]?.toDomain() }
    }

    override suspend fun recordPlayed(songId: Long) {
        // Counters + history row in one transaction (one change notification instead of two).
        songDao.recordPlay(songId, playedAt = System.currentTimeMillis())
    }

    override fun observeAllSongs(): Flow<List<Song>> =
        songDao.observeAll().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override fun observeAlbums(): Flow<List<Album>> =
        albumDao.observeAll().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override fun observeRecentAlbums(limit: Int): Flow<List<Album>> =
        albumDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override fun observeAlbumRecentPlays(sinceMillis: Long): Flow<List<AlbumRecentPlays>> =
        playbackHistoryDao.observeAlbumRecentPlays(sinceMillis).map { rows ->
            rows.map { AlbumRecentPlays(albumId = it.albumId, plays = it.plays, days = it.days) }
        }

    override fun observeSavedSongIds(): Flow<List<Long>> = playlistDao.observeSavedSongIds()

    override fun observeLibraryStats(): Flow<LibraryStats> = combine(
        songDao.observeTotals(),
        albumDao.observeCount(),
        artistDao.observeCount(),
        playlistDao.observeCount(),
    ) { totals, albums, artists, playlists ->
        LibraryStats(
            songCount = totals.count,
            albumCount = albums,
            artistCount = artists,
            playlistCount = playlists,
            totalSizeBytes = totals.sizeBytes,
            totalDurationMs = totals.durationMs,
        )
    }

    override fun observeArtists(): Flow<List<Artist>> =
        artistDao.observeAll().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override fun searchSongs(query: String): Flow<List<Song>> =
        songDao.search(escapeLike(query)).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override fun searchAlbums(query: String): Flow<List<Album>> =
        albumDao.search(escapeLike(query)).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override fun searchArtists(query: String): Flow<List<Artist>> =
        artistDao.search(escapeLike(query)).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    /** Escapes LIKE wildcards so a query like "50%" or "a_b" matches literally (the DAOs use ESCAPE '\'). */
    private fun escapeLike(query: String): String = query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    override fun observeIsFavorite(songId: Long): Flow<Boolean> = songDao.observeIsFavorite(songId)

    override fun observeLyrics(songId: Long): Flow<String?> = songDao.observeLyrics(songId)

    override suspend fun setLyrics(songId: Long, lyrics: String?) {
        songDao.setLyrics(songId, lyrics?.take(MAX_LYRICS_LENGTH)?.takeIf(String::isNotBlank))
    }

    override fun observeAlbum(albumId: Long): Flow<Album?> =
        albumDao.observeById(albumId).map { it?.toDomain() }

    // Sorted in Kotlin by albumTrackOrder (disc, then track, missing track
    // numbers last) rather than in SQL, where NULLs would sort first.
    override fun observeAlbumSongs(albumId: Long): Flow<List<Song>> =
        songDao.observeByAlbum(albumId).map { list ->
            list.map { it.toDomain() }.sortedWith(albumTrackOrder)
        }.flowOn(Dispatchers.Default)

    override fun observeArtist(artistId: Long): Flow<Artist?> =
        artistDao.observeById(artistId).map { it?.toDomain() }

    override fun observeArtistAlbums(artistId: Long): Flow<List<Album>> =
        albumDao.observeByAlbumArtist(artistId).map { list ->
            list.map { it.toDomain() }.distinctBy { it.id }.sortedWith(albumReleaseOrder)
        }.flowOn(Dispatchers.Default)

    override suspend fun findAlbumArtistId(albumId: Long): Long? = albumDao.findById(albumId)?.albumArtistId

    override fun observeArtistSongs(artistId: Long): Flow<List<Song>> =
        songDao.observeByArtist(artistId).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observeAllWithCounts().map { list -> list.map { it.toDomain() } }

    override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
        playlistDao.observeWithCountById(playlistId).map { it?.toDomain() }

    override fun observePlaylistEntries(playlistId: Long): Flow<List<PlaylistEntry>> =
        playlistDao.observeEntries(playlistId).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override suspend fun createPlaylist(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        val now = System.currentTimeMillis()
        return playlistDao.insert(PlaylistEntity(name = trimmed, createdAt = now, updatedAt = now))
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        playlistDao.rename(playlistId, trimmed, System.currentTimeMillis())
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.delete(playlistId)
    }

    override suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        playlistDao.appendSong(playlistId, songId, System.currentTimeMillis())
    }

    override suspend fun addSongsToPlaylistSkippingDuplicates(playlistId: Long, songIds: List<Long>): Int =
        playlistDao.appendSongsSkippingDuplicates(playlistId, songIds, System.currentTimeMillis())

    override suspend fun favoriteSongs(songIds: List<Long>): Int =
        songDao.favoriteMissing(songIds, System.currentTimeMillis())

    override suspend fun removePlaylistEntry(entryId: Long) {
        playlistDao.removeEntry(entryId, System.currentTimeMillis())
    }

    override suspend fun reorderPlaylist(playlistId: Long, orderedEntryIds: List<Long>) {
        playlistDao.reorder(playlistId, orderedEntryIds)
    }

    override fun observeFolders(): Flow<List<MusicFolder>> =
        musicFolderDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun setFolderEnabled(folderId: Long, isEnabled: Boolean) {
        musicFolderDao.setEnabled(folderId, isEnabled)
    }

    override suspend fun removeFolder(folderId: Long) {
        // Cascades: removing a folder also removes every song row that
        // points at it (see the CASCADE foreign key on SongEntity.folderId),
        // and from there, its playlist entries and play history too.
        val folder = musicFolderDao.findById(folderId)
        // The folder-id -> tree-URI lookup is cached (see treeUriOf); change or remove a folder and the entry goes.
        treeUriCache.remove(folderId)
        musicFolderDao.delete(folderId)
        duplicateFingerprintDao.deleteForFolder(folderId)
        // Give the persisted SAF grant back; the system caps how many an app may hold.
        if (folder != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(folder.treeUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (e: Exception) {
                // Already released or never held — nothing to do.
            }
        }
    }

    override suspend fun rescanFolder(folderId: Long) {
        val folder = musicFolderDao.findById(folderId) ?: return
        LibraryScanWorker.enqueue(context, folder.id, replace = true)
    }

    override suspend fun refreshLibrary(): Boolean {
        if (musicFolderDao.getEnabled().isEmpty()) return false
        LibraryScanWorker.enqueueAll(context)
        return true
    }

    override fun cancelRefresh() {
        LibraryScanWorker.cancelAll(context)
    }

    override suspend fun renewFolderAccess(folderId: Long, treeUri: String, displayName: String) {
        val previous = musicFolderDao.findById(folderId) ?: return
        treeUriCache.remove(folderId)
        musicFolderDao.updateTreeAccess(folderId, treeUri, displayName)
        if (previous.treeUri != treeUri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(previous.treeUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        musicFolderDao.findById(folderId)?.let { LibraryScanWorker.enqueue(context, it.id, replace = true) }
    }

    override fun cancelFolderScan(folderId: Long) {
        LibraryScanWorker.cancel(context, folderId)
    }

    override suspend fun readSongTags(songId: Long) = metadataEditService.readTags(songId)

    override suspend fun applyMetadataEdits(request: com.auralis.player.domain.model.MetadataEditRequest) =
        metadataEditService.apply(request)

    override fun observeGenreNames(): Flow<List<String>> =
        genreDao.observeAll().map { list -> list.map { it.name } }

    override suspend fun setPlaylistArtwork(playlistId: Long, jpeg: ByteArray?): Boolean {
        if (jpeg == null) {
            playlistDao.setCustomArtwork(playlistId, null)
            return true
        }
        val path = artworkStore.save(jpeg) ?: return false
        playlistDao.setCustomArtwork(playlistId, path)
        return true
    }
}

private const val MAX_LYRICS_LENGTH = 100_000
