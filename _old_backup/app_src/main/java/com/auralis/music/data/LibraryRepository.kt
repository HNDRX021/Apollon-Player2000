package com.auralis.music.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Locale

class LibraryRepository(private val dao: LibraryDao) {
    val songs: Flow<List<SongEntity>> = dao.observeSongsByTitle()
    val favorites: Flow<List<SongEntity>> = dao.observeFavorites()
    val recent: Flow<List<SongEntity>> = dao.observeRecentlyPlayed(12)
    val recentAdded: Flow<List<SongEntity>> = dao.observeRecentlyAdded(12)
    val mostPlayed: Flow<List<SongEntity>> = dao.observeMostPlayed(12)
    val albums: Flow<List<AlbumSummary>> = dao.observeAlbums()
    val artists: Flow<List<String>> = dao.observeArtists()
    val genres: Flow<List<String>> = dao.observeGenres()
    val folders: Flow<List<MusicFolderEntity>> = dao.observeMusicFolders()
    val playlists: Flow<List<PlaylistEntity>> = dao.observePlaylists()

    val stats: Flow<LibraryStats> = combine(
        dao.observeSongCount(),
        dao.observeAlbumCount(),
        dao.observeArtistCount(),
        dao.observePlaylistCount()
    ) { songs, albums, artists, playlists ->
        LibraryStats(songs, albums, artists, playlists)
    }

    fun search(query: String): Flow<List<SongEntity>> = dao.search(query.trim())

    fun albumTracks(album: String, albumArtist: String): Flow<List<SongEntity>> =
        dao.observeAlbumTracks(album, albumArtist)

    fun artistTracks(artist: String): Flow<List<SongEntity>> = dao.observeArtistTracks(artist)

    fun playlist(id: Long): Flow<PlaylistEntity?> = dao.observePlaylist(id)
    fun playlistSongs(id: Long): Flow<List<SongEntity>> = dao.observePlaylistSongs(id)

    suspend fun getSong(id: Long): SongEntity? = dao.getSong(id)

    suspend fun addFolder(treeUri: String, displayName: String): Long {
        val existing = foldersSnapshot().firstOrNull { it.treeUri == treeUri }
        if (existing != null) return existing.id
        return dao.insertFolder(MusicFolderEntity(treeUri = treeUri, displayName = displayName))
    }

    private suspend fun foldersSnapshot(): List<MusicFolderEntity> = kotlinx.coroutines.flow.first(folders)

    suspend fun removeFolder(folder: MusicFolderEntity) = dao.deleteFolder(folder.id)

    suspend fun upsertSong(song: SongEntity): Long = dao.insertSong(song)
    suspend fun updateSong(song: SongEntity) = dao.updateSong(song)
    suspend fun findByUri(uri: String): SongEntity? = dao.findByUri(uri)
    suspend fun findByFingerprint(folderId: Long, fingerprint: String): SongEntity? =
        dao.findByFingerprint(folderId, fingerprint)
    suspend fun deleteMissingInFolder(folderId: Long, seenUris: List<String>) {
        if (seenUris.isEmpty()) dao.deleteAllInFolder(folderId) else dao.deleteMissingInFolder(folderId, seenUris)
    }

    suspend fun setFavorite(songId: Long, favorite: Boolean) = dao.setFavorite(songId, favorite)
    suspend fun registerPlay(songId: Long) = dao.registerPlay(songId, System.currentTimeMillis())
    suspend fun updatePlaybackPosition(songId: Long, positionMs: Long) = dao.updatePlaybackPosition(songId, positionMs)

    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(PlaylistEntity(name = name.trim()))
    suspend fun deletePlaylist(playlistId: Long) = dao.deletePlaylist(playlistId)

    suspend fun setPlaylistSongs(playlistId: Long, songIds: List<Long>) {
        dao.clearPlaylist(playlistId)
        songIds.forEachIndexed { index, songId ->
            dao.upsertPlaylistSong(PlaylistSongCrossRef(playlistId, songId, index))
        }
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) = dao.removeSongFromPlaylist(playlistId, songId)
}

data class LibraryStats(
    val songs: Int,
    val albums: Int,
    val artists: Int,
    val playlists: Int
)

fun SongEntity.searchBlob(): String =
    listOf(title, artist, album, albumArtist, genre, composer, fileName)
        .joinToString(" ")
        .lowercase(Locale.ROOT)
