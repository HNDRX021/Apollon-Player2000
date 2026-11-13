package com.auralis.player.domain.model

import com.auralis.player.data.local.entity.AlbumEntity

data class Album(
    val id: Long,
    val title: String,
    val albumArtistId: Long?,
    val year: Int?,
    val artworkUri: String?,
    val trackCount: Int,
    val discCount: Int,
    val isFavorite: Boolean = false,
    val dateAdded: Long = 0L,
    /** `yyyy`, `yyyy-MM` or `yyyy-MM-dd` (only the parts the tags provide), or null when unknown. */
    val releaseDate: String? = null,
)

fun AlbumEntity.toDomain(): Album = Album(
    id = id,
    title = title,
    albumArtistId = albumArtistId,
    year = year,
    artworkUri = artworkUri,
    trackCount = trackCount,
    discCount = discCount,
    isFavorite = isFavorite,
    dateAdded = dateAdded,
    // A blank value is the scanner's "looked, found no date" marker.
    releaseDate = releaseDate?.ifBlank { null },
)
