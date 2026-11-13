package com.auralis.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.auralis.player.data.local.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

/**
 * The Artists section lists *album artists* only: an artist row shows up
 * there when at least one available song sits on an album whose album-artist
 * tag points at it. Track-level performers (e.g. "Artist ft. Guest" from a
 * feature credit) still exist in the `artists` table so song rows can show
 * the full credit, but they never appear as artists of their own.
 *
 * A missing cover on the artist row falls back to one of its albums' covers.
 */
private const val ALBUM_ARTIST_SELECT = """
    SELECT artists.id AS id, artists.name AS name, artists.sortName AS sortName,
        COALESCE(
            artists.artworkUri,
            (SELECT albums.artworkUri FROM albums
                WHERE albums.albumArtistId = artists.id AND albums.artworkUri IS NOT NULL LIMIT 1)
        ) AS artworkUri,
        artists.isFavorite AS isFavorite
    FROM artists
    WHERE EXISTS (
        SELECT 1 FROM albums INNER JOIN songs ON songs.albumId = albums.id
        WHERE albums.albumArtistId = artists.id AND songs.isAvailable = 1
    )
"""

@Dao
interface ArtistDao {

    @Query("$ALBUM_ARTIST_SELECT ORDER BY artists.sortName ASC")
    fun observeAll(): Flow<List<ArtistEntity>>

    /** [query] must have LIKE wildcards pre-escaped with a backslash. */
    @Query("$ALBUM_ARTIST_SELECT AND artists.name LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY artists.sortName ASC LIMIT 50")
    fun search(query: String): Flow<List<ArtistEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM artists
        WHERE EXISTS (
            SELECT 1 FROM albums INNER JOIN songs ON songs.albumId = albums.id
            WHERE albums.albumArtistId = artists.id AND songs.isAvailable = 1
        )
        """,
    )
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT artists.id AS id, artists.name AS name, artists.sortName AS sortName,
            COALESCE(
                artists.artworkUri,
                (SELECT albums.artworkUri FROM albums
                    WHERE albums.albumArtistId = artists.id AND albums.artworkUri IS NOT NULL LIMIT 1)
            ) AS artworkUri,
            artists.isFavorite AS isFavorite
        FROM artists WHERE artists.id = :id
        """,
    )
    fun observeById(id: Long): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun findById(id: Long): ArtistEntity?

    @Query("SELECT * FROM artists WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ArtistEntity?

    @Query("UPDATE artists SET isFavorite = :isFavorite WHERE id = :artistId")
    suspend fun setFavorite(artistId: Long, isFavorite: Boolean)

    @Query("UPDATE artists SET artworkUri = :path WHERE id = :artistId AND artworkUri IS NULL")
    suspend fun setArtworkIfMissing(artistId: Long, path: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(artist: ArtistEntity): Long
}
