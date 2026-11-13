package com.auralis.player.domain.model

import com.auralis.player.data.local.dao.PlaylistWithCount

/**
 * [artworkUri] is the cover the user picked for this playlist ([hasCustomArtwork]), or null. Playlists
 * never borrow a track's cover: without a picked picture the UI draws the accent-colour cover.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val updatedAt: Long,
    val artworkUri: String? = null,
    val hasCustomArtwork: Boolean = false,
)

fun PlaylistWithCount.toDomain(): Playlist = Playlist(
    id = playlist.id,
    name = playlist.name,
    songCount = songCount,
    updatedAt = playlist.updatedAt,
    artworkUri = playlist.customArtworkUri,
    hasCustomArtwork = playlist.customArtworkUri != null,
)
