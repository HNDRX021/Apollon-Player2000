package com.auralis.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** File path of a cover the user picked for this playlist; null means the automatic cover is used. */
    val customArtworkUri: String? = null,
)
