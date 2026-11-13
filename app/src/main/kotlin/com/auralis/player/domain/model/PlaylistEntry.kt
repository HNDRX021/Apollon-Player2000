package com.auralis.player.domain.model

import com.auralis.player.data.local.dao.PlaylistSongRow
import com.auralis.player.data.local.dao.SongWithMetadata

/** One slot in a playlist. [entryId] identifies the slot, so the same [song] can appear twice. */
data class PlaylistEntry(
    val entryId: Long,
    val song: Song,
)

fun PlaylistSongRow.toDomain(): PlaylistEntry = PlaylistEntry(
    entryId = crossRefId,
    song = SongWithMetadata(song = song, artistName = artistName, albumTitle = albumTitle, artworkUri = artworkUri).toDomain(),
)
