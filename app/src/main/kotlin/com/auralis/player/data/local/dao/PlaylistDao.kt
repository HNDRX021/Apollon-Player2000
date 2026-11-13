package com.auralis.player.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.auralis.player.data.local.entity.PlaylistEntity
import com.auralis.player.data.local.entity.PlaylistSongCrossRef
import com.auralis.player.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val songCount: Int,
)

/** A song inside a playlist, plus the cross-ref id needed to remove/reorder that specific entry. */
data class PlaylistSongRow(
    @Embedded val song: SongEntity,
    val artistName: String?,
    val albumTitle: String?,
    val artworkUri: String?,
    val crossRefId: Long,
)

// No cover is derived here: a playlist's cover is the accent colour (or the picture the user picked,
// which lives on the playlist row itself).
private const val PLAYLIST_WITH_COUNT_SELECT = """
    SELECT playlists.*,
        (SELECT COUNT(*) FROM playlist_songs WHERE playlist_songs.playlistId = playlists.id) AS songCount
    FROM playlists
"""

@Dao
interface PlaylistDao {

    @Query("SELECT COUNT(*) FROM playlists")
    fun observeCount(): Flow<Int>

    /** Every song that is in at least one playlist, once, however many playlists it appears in. */
    @Query("SELECT DISTINCT songId FROM playlist_songs")
    fun observeSavedSongIds(): Flow<List<Long>>

    @Query("$PLAYLIST_WITH_COUNT_SELECT ORDER BY playlists.updatedAt DESC")
    fun observeAllWithCounts(): Flow<List<PlaylistWithCount>>

    @Query("$PLAYLIST_WITH_COUNT_SELECT WHERE playlists.id = :playlistId")
    fun observeWithCountById(playlistId: Long): Flow<PlaylistWithCount?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String, updatedAt: Long)

    /** Sets (or, with null, removes) the cover the user picked. Deliberately leaves updatedAt alone, so it doesn't reorder "Recents". */
    @Query("UPDATE playlists SET customArtworkUri = :path WHERE id = :playlistId")
    suspend fun setCustomArtwork(playlistId: Long, path: String?)

    // Deleting a playlist cascades to its playlist_songs rows via the FK;
    // the songs themselves are untouched.
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun delete(playlistId: Long)

    // Lyrics are returned as NULL rather than read (`songs.*` would copy every song's full lyrics text out of the
    // database on each emission; a playlist entry is shown as a `Song`, which has no lyrics). See SONG_LIST_SELECT
    // in SongDao: a new `songs` column has to be added to this list too.
    @Query(
        """
        SELECT songs.id, songs.documentId, songs.folderId, songs.filePath, songs.displayName, songs.fileSize,
            songs.lastModified, songs.contentFingerprint, songs.title, songs.titleSortKey, songs.artistId,
            songs.albumId, songs.genreId, songs.composer, songs.year, songs.trackNumber, songs.totalTracks,
            songs.discNumber, songs.totalDiscs, songs.durationMs, songs.bitrateKbps, songs.sampleRateHz,
            songs.mimeType, songs.dateAdded, songs.dateModified, songs.isAvailable, songs.isFavorite,
            songs.favoritedAt, songs.playCount, songs.lastPlayedAt, CAST(NULL AS TEXT) AS lyrics,
            artists.name AS artistName, albums.title AS albumTitle,
            albums.artworkUri AS artworkUri, playlist_songs.id AS crossRefId
        FROM songs
        INNER JOIN playlist_songs ON playlist_songs.songId = songs.id
        LEFT JOIN artists ON artists.id = songs.artistId
        LEFT JOIN albums ON albums.id = songs.albumId
        WHERE playlist_songs.playlistId = :playlistId AND songs.isAvailable = 1
        ORDER BY playlist_songs.position ASC
        """,
    )
    fun observeEntries(playlistId: Long): Flow<List<PlaylistSongRow>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef): Long

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touch(playlistId: Long, updatedAt: Long)

    @Transaction
    suspend fun appendSong(playlistId: Long, songId: Long, now: Long) {
        val position = nextPosition(playlistId)
        addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = songId, position = position, addedAt = now),
        )
        touch(playlistId, now)
    }

    /** Ids of every song the playlist already holds (unavailable songs included, so they are never added twice). */
    @Query("SELECT DISTINCT songId FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongIds(playlistId: Long): List<Long>

    /**
     * Appends [songIds], in the given order, to the END of the playlist, skipping every song the playlist
     * already holds and any repeat inside [songIds] itself. Existing entries keep their positions; the new
     * ones continue from the next free position. One transaction, so a failure adds nothing.
     * Returns how many songs were actually added; the playlist is only "touched" (Recents) when that is > 0.
     * [appendSong] stays as it is: adding the same song twice by hand is still allowed.
     */
    @Transaction
    suspend fun appendSongsSkippingDuplicates(playlistId: Long, songIds: List<Long>, now: Long): Int {
        val present = getSongIds(playlistId).toHashSet()
        var position = nextPosition(playlistId)
        var added = 0
        for (songId in songIds) {
            if (!present.add(songId)) continue
            addSongToPlaylist(
                PlaylistSongCrossRef(playlistId = playlistId, songId = songId, position = position++, addedAt = now),
            )
            added++
        }
        if (added > 0) touch(playlistId, now)
        return added
    }

    @Query("DELETE FROM playlist_songs WHERE id = :crossRefId")
    suspend fun removeCrossRef(crossRefId: Long)

    /** Marks the playlist that owns entry [crossRefId] as just modified (no-op when the entry is already gone). */
    @Query(
        "UPDATE playlists SET updatedAt = :updatedAt " +
            "WHERE id = (SELECT playlistId FROM playlist_songs WHERE id = :crossRefId)",
    )
    suspend fun touchOwnerOfEntry(crossRefId: Long, updatedAt: Long)

    /** Removes one entry and bumps its playlist's updatedAt, so "Recents" reflects the change. */
    @Transaction
    suspend fun removeEntry(crossRefId: Long, now: Long) {
        touchOwnerOfEntry(crossRefId, now)
        removeCrossRef(crossRefId)
    }

    @Query("UPDATE playlist_songs SET position = :newPosition WHERE id = :crossRefId")
    suspend fun updatePosition(crossRefId: Long, newPosition: Int)

    /**
     * Reorders by cross-ref [id], never by songId — the same song can
     * appear twice in one playlist, and keying off songId would move both.
     */
    @Transaction
    suspend fun reorder(playlistId: Long, orderedCrossRefIds: List<Long>) {
        orderedCrossRefIds.forEachIndexed { index, crossRefId ->
            updatePosition(crossRefId, index)
        }
        touch(playlistId, System.currentTimeMillis())
    }
}
