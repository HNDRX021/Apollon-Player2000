package com.auralis.player.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only play log. Fast aggregates ([SongEntity.playCount] /
 * [SongEntity.lastPlayedAt]) are kept directly on the song row for
 * performance and drive the Home screen's song lists; this table holds the
 * full history, which Home's "Your Favorites" albums use to find what was
 * played on many different days recently (see PlaybackHistoryDao).
 */
@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["songId"]), Index(value = ["playedAt"])],
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: Long,
    val playedAt: Long,
)
