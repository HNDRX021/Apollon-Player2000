package com.auralis.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.auralis.player.data.local.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY title ASC")
    fun observeAll(): Flow<List<AlbumEntity>>

    /** Matches the album title or its album artist's name. [query] must have LIKE wildcards pre-escaped with a backslash. */
    @Query(
        "SELECT albums.* FROM albums LEFT JOIN artists ON artists.id = albums.albumArtistId " +
            "WHERE albums.title LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR artists.name LIKE '%' || :query || '%' ESCAPE '\\' " +
            "ORDER BY albums.title ASC LIMIT 50",
    )
    fun search(query: String): Flow<List<AlbumEntity>>

    @Query("SELECT COUNT(*) FROM albums")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM albums ORDER BY dateAdded DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeById(id: Long): Flow<AlbumEntity?>

    /** Albums whose album-artist tag is [artistId] and that still have at least one available song. */
    @Query(
        "SELECT * FROM albums WHERE albumArtistId = :artistId AND EXISTS " +
            "(SELECT 1 FROM songs WHERE songs.albumId = albums.id AND songs.isAvailable = 1)",
    )
    fun observeByAlbumArtist(artistId: Long): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun findById(id: Long): AlbumEntity?

    @Query("SELECT * FROM albums WHERE title = :title AND albumArtistId IS :albumArtistId LIMIT 1")
    suspend fun findByTitleAndArtist(title: String, albumArtistId: Long?): AlbumEntity?

    @Query("UPDATE albums SET isFavorite = :isFavorite WHERE id = :albumId")
    suspend fun setFavorite(albumId: Long, isFavorite: Boolean)

    /** Only fills a missing cover; an album's first extracted artwork wins. */
    @Query("UPDATE albums SET artworkUri = :path WHERE id = :albumId AND artworkUri IS NULL")
    suspend fun setArtworkIfMissing(albumId: Long, path: String)

    /**
     * Keeps the most precise release date, and among equally precise ones the earliest
     * (ISO strings compare chronologically). A blank [date] only records "no date found".
     */
    @Query(
        "UPDATE albums SET releaseDate = :date WHERE id = :albumId AND (releaseDate IS NULL " +
            "OR length(:date) > length(releaseDate) " +
            "OR (length(:date) = length(releaseDate) AND :date < releaseDate))",
    )
    suspend fun setReleaseDateIfBetter(albumId: Long, date: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(album: AlbumEntity): Long

    // --- Used by the metadata editor (Edit File) --------------------------------

    /** Renames an album row in place, so its id, favorite state and cover survive a title / album-artist edit. */
    @Query("UPDATE albums SET title = :title, albumArtistId = :albumArtistId WHERE id = :albumId")
    suspend fun updateIdentity(albumId: Long, title: String, albumArtistId: Long?)

    /** Unlike [setArtworkIfMissing] this overwrites the cover; null removes it. */
    @Query("UPDATE albums SET artworkUri = :path WHERE id = :albumId")
    suspend fun setArtwork(albumId: Long, path: String?)

    /** Overwrites the release date; an empty string records "no date" (see [AlbumEntity.releaseDate]). */
    @Query("UPDATE albums SET releaseDate = :date WHERE id = :albumId")
    suspend fun setReleaseDate(albumId: Long, date: String?)

    /** Removes an album row that no song points at any more (after its last track was moved elsewhere). */
    @Query("DELETE FROM albums WHERE id = :albumId AND NOT EXISTS (SELECT 1 FROM songs WHERE songs.albumId = :albumId)")
    suspend fun deleteIfUnused(albumId: Long)

    @Query(
        """
        UPDATE albums SET
            trackCount = (SELECT COUNT(*) FROM songs WHERE songs.albumId = albums.id AND songs.isAvailable = 1),
            discCount = (SELECT COALESCE(MAX(songs.discNumber), 1) FROM songs WHERE songs.albumId = albums.id AND songs.isAvailable = 1),
            year = (SELECT MIN(songs.year) FROM songs WHERE songs.albumId = albums.id AND songs.isAvailable = 1 AND songs.year IS NOT NULL)
        WHERE id = :albumId
        """,
    )
    suspend fun updateCounts(albumId: Long)
}
