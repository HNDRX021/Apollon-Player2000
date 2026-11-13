package com.auralis.player.domain.model

/** How much an album was played within a window of the listening history: [plays] over [days] different local days. */
data class AlbumRecentPlays(
    val albumId: Long,
    val plays: Int,
    val days: Int,
)
