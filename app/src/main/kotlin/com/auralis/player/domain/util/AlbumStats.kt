package com.auralis.player.domain.util

import com.auralis.player.domain.model.Song
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sum of the real durations of [songs]. A track whose duration is unknown
 * (stored as 0 or a negative value) contributes nothing instead of corrupting the total.
 */
fun albumTotalDurationMs(songs: List<Song>): Long = songs.sumOf { it.durationMs.coerceAtLeast(0L) }

/** A total album duration split into the units shown to the user. */
data class AlbumDurationParts(val hours: Int, val minutes: Int, val seconds: Int)

/**
 * Splits [totalMs] for display. Returns null when there is no usable duration.
 * Under ten minutes keeps seconds ("7 min 32 sec", good for EPs and singles);
 * longer albums are rounded to the nearest minute ("42 min", "1 hr 12 min").
 */
fun albumDurationParts(totalMs: Long): AlbumDurationParts? {
    if (totalMs <= 0L) return null
    val totalSeconds = (totalMs + 500L) / 1000L
    if (totalSeconds <= 0L) return null
    if (totalSeconds < 600L) {
        return AlbumDurationParts(hours = 0, minutes = (totalSeconds / 60L).toInt(), seconds = (totalSeconds % 60L).toInt())
    }
    val totalMinutes = (totalSeconds + 30L) / 60L
    return AlbumDurationParts(hours = (totalMinutes / 60L).toInt(), minutes = (totalMinutes % 60L).toInt(), seconds = 0)
}

/**
 * Formats a normalized release date (`yyyy`, `yyyy-MM`, `yyyy-MM-dd`, see
 * [normalizeReleaseDate]) at the precision it actually has: "13 October 2024",
 * "October 2024" or "2024". Returns null when there is no usable date; never invents
 * a month or day.
 */
fun formatReleaseDate(releaseDate: String?, locale: Locale = Locale.getDefault()): String? {
    val normalized = normalizeReleaseDate(releaseDate?.takeIf { it.isNotBlank() }) ?: return null
    return runCatching {
        when (normalized.length) {
            4 -> normalized
            7 -> YearMonth.parse(normalized).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
            else -> LocalDate.parse(normalized).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
        }
    }.getOrElse { normalized.take(4) } // e.g. an impossible day like 2024-02-31: fall back to the year
}
