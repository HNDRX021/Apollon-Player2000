package com.auralis.player.settings

/**
 * The albums and songs Home showed as "Today's Suggestions" / "Today's Songs", saved together with
 * the local date ([dayKey], `yyyy-MM-dd`) they were picked for. Home keeps showing exactly these
 * until the date changes, so the lists do not reshuffle each time the screen is opened.
 */
data class DailyPicks(
    val dayKey: String? = null,
    val albumIds: List<Long> = emptyList(),
    val songIds: List<Long> = emptyList(),
)
