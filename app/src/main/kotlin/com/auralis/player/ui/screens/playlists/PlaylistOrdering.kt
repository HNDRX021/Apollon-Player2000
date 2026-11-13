package com.auralis.player.ui.screens.playlists

import com.auralis.player.domain.model.Playlist
import com.auralis.player.domain.model.PlaylistEntry

/**
 * How the tracks of one playlist are shown. [CUSTOM] is the order the user arranged (and the only one
 * that can be reordered by dragging); the others are views over it and never change what is stored.
 */
enum class PlaylistSort(val label: String) {
    CUSTOM("Custom order"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration"),
}

/** How the playlists in the library are ordered. */
enum class PlaylistLibrarySort(val label: String) {
    RECENTS("Recents"),
    ALPHABETICAL("A–Z"),
}

/** Entries whose title, artist or album contains [query] (ignoring case); everything for a blank query. */
fun List<PlaylistEntry>.filteredBy(query: String): List<PlaylistEntry> {
    val needle = query.trim()
    if (needle.isEmpty()) return this
    return filter { entry ->
        val song = entry.song
        song.title.contains(needle, ignoreCase = true) ||
            song.artistName.orEmpty().contains(needle, ignoreCase = true) ||
            song.albumTitle.orEmpty().contains(needle, ignoreCase = true)
    }
}

/**
 * Entries ordered for display. Sorting is stable, so tracks that compare equal (and duplicates of the
 * same song) keep the order the user gave them. Missing artists and albums go last.
 */
fun List<PlaylistEntry>.sortedFor(sort: PlaylistSort): List<PlaylistEntry> = when (sort) {
    PlaylistSort.CUSTOM -> this
    PlaylistSort.TITLE -> sortedBy { it.song.title.lowercase() }
    PlaylistSort.ARTIST -> sortedWith(compareBy<PlaylistEntry> { it.song.artistName.isNullOrBlank() }.thenBy { it.song.artistName.orEmpty().lowercase() })
    PlaylistSort.ALBUM -> sortedWith(compareBy<PlaylistEntry> { it.song.albumTitle.isNullOrBlank() }.thenBy { it.song.albumTitle.orEmpty().lowercase() })
    PlaylistSort.DURATION -> sortedBy { it.song.durationMs }
}

/** A copy of this list with the item at [from] moved to [to]; the list itself when either index is out of range. */
fun <T> List<T>.movedItem(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val result = toMutableList()
    result.add(to, result.removeAt(from))
    return result
}

/** Playlists whose name contains [query], in the requested order. */
fun List<Playlist>.forLibrary(sort: PlaylistLibrarySort, query: String): List<Playlist> {
    val needle = query.trim()
    val matching = if (needle.isEmpty()) this else filter { it.name.contains(needle, ignoreCase = true) }
    return when (sort) {
        PlaylistLibrarySort.RECENTS -> matching.sortedByDescending { it.updatedAt }
        PlaylistLibrarySort.ALPHABETICAL -> matching.sortedBy { it.name.lowercase() }
    }
}

/** "1 hr 12 min", "45 min" or "1 min" for the total length of a playlist; empty for no duration at all. */
fun formatTotalDuration(totalMs: Long): String {
    if (totalMs <= 0L) return ""
    val totalMinutes = (totalMs / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours hr"
        else -> "$hours hr $minutes min"
    }
}
