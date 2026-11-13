package com.auralis.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.auralis.player.data.local.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

/** How much one album was played in a window of the history: total [plays] on [days] different local days. */
data class AlbumRecentPlaysRow(
    val albumId: Long,
    val plays: Int,
    val days: Int,
)

@Dao
interface PlaybackHistoryDao {

    @Insert
    suspend fun insert(entry: PlaybackHistoryEntity): Long

    /**
     * Per album, how many plays and how many different (local) calendar days of plays the history
     * holds since [sinceMillis]. Albums with no plays in that window are not returned.
     */
    @Query(
        """
        SELECT songs.albumId AS albumId,
            COUNT(*) AS plays,
            COUNT(DISTINCT date(playback_history.playedAt / 1000, 'unixepoch', 'localtime')) AS days
        FROM playback_history
        INNER JOIN songs ON songs.id = playback_history.songId
        WHERE playback_history.playedAt >= :sinceMillis
            AND songs.albumId IS NOT NULL
            AND songs.isAvailable = 1
        GROUP BY songs.albumId
        """,
    )
    fun observeAlbumRecentPlays(sinceMillis: Long): Flow<List<AlbumRecentPlaysRow>>
}
