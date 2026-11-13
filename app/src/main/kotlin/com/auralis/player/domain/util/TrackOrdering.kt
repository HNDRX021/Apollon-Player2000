package com.auralis.player.domain.util

import com.auralis.player.domain.model.Song

/**
 * Canonical ordering for tracks within an album. [Song.discNumber] defaults
 * to 1 (never null) at the entity level, so a single-disc album collapses
 * naturally into plain track-number order — there is no special-casing
 * needed for the common case.
 */
val albumTrackOrder: Comparator<Song> =
    compareBy<Song> { it.discNumber }
        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
        .thenBy { it.title.lowercase() }
