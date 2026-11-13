package com.auralis.player.domain.model

import com.auralis.player.data.local.entity.GenreEntity

data class Genre(
    val id: Long,
    val name: String,
)

fun GenreEntity.toDomain(): Genre = Genre(id = id, name = name)
