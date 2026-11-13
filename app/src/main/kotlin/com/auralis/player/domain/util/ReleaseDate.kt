package com.auralis.player.domain.util

import com.auralis.player.domain.model.Album
import java.util.Locale

private val RELEASE_DATE = Regex("""^\s*(\d{4})(?:\D?(\d{2}))?(?:\D?(\d{2}))?""")

/**
 * Turns the many shapes of a "date" tag ("2024", "2024-10", "2024-10-13",
 * "20241013", "2024-10-13T00:00:00Z", "2024.10.13", ...) into one sortable
 * ISO-like string: `yyyy`, `yyyy-MM` or `yyyy-MM-dd`, keeping only the parts
 * that are actually present and valid. Returns null when there is no usable year.
 * Never invents a month or day.
 */
fun normalizeReleaseDate(raw: String?): String? {
    val match = RELEASE_DATE.find(raw ?: return null) ?: return null
    val year = match.groupValues[1].toInt()
    if (year !in 1000..2999) return null
    val month = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..12 }
    val day = if (month != null) match.groupValues[3].toIntOrNull()?.takeIf { it in 1..31 } else null
    return when {
        month == null -> "%04d".format(Locale.ROOT, year)
        day == null -> "%04d-%02d".format(Locale.ROOT, year, month)
        else -> "%04d-%02d-%02d".format(Locale.ROOT, year, month, day)
    }
}

/**
 * Key an album is ordered by: its release date when known (any precision),
 * otherwise its year, otherwise nothing. ISO strings of different precision
 * still sort chronologically ("2017" < "2017-07" < "2017-07-27" < "2018").
 */
val Album.releaseSortKey: String?
    get() = releaseDate?.takeIf { it.isNotBlank() } ?: year?.let { "%04d".format(Locale.ROOT, it) }

/** The year to show for an album, from its release date if present, else the stored year. */
val Album.releaseYear: Int?
    get() = releaseDate?.take(4)?.toIntOrNull() ?: year

/** Oldest release first; albums with no date at all go last, then by title. */
val albumReleaseOrder: Comparator<Album> =
    compareBy<Album, String?>(nullsLast<String>()) { it.releaseSortKey }.thenBy { it.title.lowercase() }
