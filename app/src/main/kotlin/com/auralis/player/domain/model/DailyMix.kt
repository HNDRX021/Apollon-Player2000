package com.auralis.player.domain.model

/**
 * What Home shows for the current calendar day: the "Today's Suggestions" [albums], and [songs],
 * the whole library in the day's fixed order (its saved first page, then every other song).
 * "Today's Music" shows the first part of [songs] and reveals more a page at a time.
 */
data class DailyMix(
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
)
