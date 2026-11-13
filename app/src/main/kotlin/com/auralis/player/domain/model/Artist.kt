package com.auralis.player.domain.model

import com.auralis.player.data.local.entity.ArtistEntity

data class Artist(
    val id: Long,
    val name: String,
    val artworkUri: String?,
    val isFavorite: Boolean = false,
)

fun ArtistEntity.toDomain(): Artist = Artist(
    id = id,
    name = name,
    artworkUri = artworkUri,
    isFavorite = isFavorite,
)
