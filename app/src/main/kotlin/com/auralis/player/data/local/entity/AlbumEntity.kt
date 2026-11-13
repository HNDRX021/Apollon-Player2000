package com.auralis.player.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumArtistId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["albumArtistId"]), Index(value = ["title"])],
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val albumArtistId: Long? = null,
    val year: Int? = null,
    val artworkUri: String? = null,
    /** Denormalized counts, refreshed by the scanner after each import pass. */
    val trackCount: Int = 0,
    val discCount: Int = 1,
    val isFavorite: Boolean = false,
    val dateAdded: Long,
    /**
     * Earliest known release date (`yyyy`, `yyyy-MM` or `yyyy-MM-dd`). An empty string
     * means the scanner already looked and found no date tag, so it does not look again.
     */
    val releaseDate: String? = null,
)
