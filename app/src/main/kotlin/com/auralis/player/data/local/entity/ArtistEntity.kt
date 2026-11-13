package com.auralis.player.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artists",
    indices = [Index(value = ["name"], unique = true)],
)
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortName: String,
    val artworkUri: String? = null,
    val isFavorite: Boolean = false,
)
