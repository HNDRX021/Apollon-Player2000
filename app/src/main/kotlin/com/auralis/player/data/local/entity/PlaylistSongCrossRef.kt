package com.auralis.player.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A surrogate [id] (rather than a composite `(playlistId, songId)` key) lets
 * the same song be added to a playlist more than once. Reorder operations
 * must key off this [id], never off [songId], to disambiguate duplicates.
 */
@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["playlistId"]), Index(value = ["songId"])],
)
data class PlaylistSongCrossRef(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val addedAt: Long,
)
