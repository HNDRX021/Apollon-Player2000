package com.auralis.player.domain.util

/*
 * The confirmation texts of the album "Add Album to Favorites" / "Add Album to Playlist" actions. Pure
 * functions (no Android), so the wording for "everything added", "some were already there" and "nothing new"
 * is unit-tested instead of living inside a ViewModel toast.
 */

/**
 * Toast text after adding an album's [total] tracks to the playlist [playlistName] when [added] of them were
 * new (the rest were already in the playlist and were skipped).
 */
fun albumToPlaylistMessage(added: Int, total: Int, playlistName: String): String = when {
    added <= 0 -> "Already in $playlistName"
    added == 1 && total == 1 -> "Added to $playlistName"
    added >= total -> "Added $added songs to $playlistName"
    else -> "Added $added ${songs(added)} to $playlistName (${total - added} already there)"
}

/** Toast text after favoriting an album's [total] tracks when [added] of them were not favorites yet. */
fun albumToFavoritesMessage(added: Int, total: Int): String = when {
    added <= 0 -> "Already in Favorites"
    added >= total -> "Added ${if (added == 1) "1 song" else "$added songs"} to Favorites"
    else -> "Added $added ${songs(added)} to Favorites (${total - added} already there)"
}

private fun songs(count: Int): String = if (count == 1) "song" else "songs"
