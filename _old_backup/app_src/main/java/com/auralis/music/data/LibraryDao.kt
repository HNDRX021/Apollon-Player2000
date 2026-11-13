package com.auralis.music.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM songs ORDER BY COALESCE(lastPlayedAt, 0) DESC, dateAdded DESC")
    fun observeSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun observeSongsByTitle(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE")
    fun observeFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY playCount DESC, title COLLATE NOCASE LIMIT :limit")
    fun observeMostPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSong(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE sourceUri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): SongEntity?

    @Query("SELECT * FROM songs WHERE folderId = :folderId AND fingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(folderId: Long, fingerprint: String): SongEntity?

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%' OR albumArtist LIKE '%' || :query || '%' OR composer LIKE '%' || :query || '%' OR fileName LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE")
    fun search(query: String): Flow<List<SongEntity>>

    @Query("SELECT DISTINCT album, albumArtist, year, artworkPath FROM songs WHERE album != '' ORDER BY album COLLATE NOCASE")
    fun observeAlbums(): Flow<List<AlbumSummary>>

    @Query("SELECT DISTINCT artist FROM songs WHERE artist != '' ORDER BY artist COLLATE NOCASE")
    fun observeArtists(): Flow<List<String>>

    @Query("SELECT DISTINCT genre FROM songs WHERE genre != '' ORDER BY genre COLLATE NOCASE")
    fun observeGenres(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE album = :album AND albumArtist = :albumArtist ORDER BY COALESCE(discNumber, 1), COALESCE(trackNumber, 2147483647), title COLLATE NOCASE")
    fun observeAlbumTracks(album: String, albumArtist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album COLLATE NOCASE, COALESCE(discNumber, 1), COALESCE(trackNumber, 2147483647)")
    fun observeArtistTracks(artist: String): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM songs")
    fun observeSongCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT album || '|' || albumArtist) FROM songs WHERE album != ''")
    fun observeAlbumCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT artist) FROM songs WHERE artist != ''")
    fun observeArtistCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlists")
    fun observePlaylistCount(): Flow<Int>

    @Query("SELECT * FROM music_folders ORDER BY displayName COLLATE NOCASE")
    fun observeMusicFolders(): Flow<List<MusicFolderEntity>>

    @Query("SELECT * FROM music_folders WHERE id = :id LIMIT 1")
    suspend fun getFolder(id: Long): MusicFolderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: MusicFolderEntity): Long

    @Update
    suspend fun updateFolder(folder: MusicFolderEntity)

    @Query("DELETE FROM music_folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity): Long

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSong(id: Long)

    @Query("DELETE FROM songs WHERE folderId = :folderId AND sourceUri NOT IN (:seenUris)")
    suspend fun deleteMissingInFolder(folderId: Long, seenUris: List<String>)

    @Query("DELETE FROM songs WHERE folderId = :folderId")
    suspend fun deleteAllInFolder(folderId: Long)

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON ps.songId = s.id WHERE ps.playlistId = :playlistId ORDER BY ps.position")
    fun observePlaylistSongs(playlistId: Long): Flow<List<SongEntity>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistSong(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("UPDATE songs SET isFavorite = :favorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, favorite: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :playedAt, lastPositionMs = 0 WHERE id = :songId")
    suspend fun registerPlay(songId: Long, playedAt: Long)

    @Query("UPDATE songs SET lastPositionMs = :positionMs WHERE id = :songId")
    suspend fun updatePlaybackPosition(songId: Long, positionMs: Long)
}

data class AlbumSummary(
    val album: String,
    val albumArtist: String,
    val year: Int?,
    val artworkPath: String?
)
