package com.auralis.player.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.auralis.player.R
import com.auralis.player.domain.model.Album
import com.auralis.player.domain.util.albumDurationParts
import com.auralis.player.domain.util.formatReleaseDate

/** "1 hr 12 min", "42 min" or "7 min 32 sec"; null when no track has a usable duration. */
@Composable
fun albumDurationText(totalMs: Long): String? {
    val parts = albumDurationParts(totalMs) ?: return null
    return when {
        parts.hours > 0 && parts.minutes > 0 -> stringResource(R.string.album_duration_hr_min, parts.hours, parts.minutes)
        parts.hours > 0 -> stringResource(R.string.album_duration_hr, parts.hours)
        parts.minutes > 0 && parts.seconds > 0 -> stringResource(R.string.album_duration_min_sec, parts.minutes, parts.seconds)
        parts.minutes > 0 -> stringResource(R.string.album_duration_min, parts.minutes)
        else -> stringResource(R.string.album_duration_sec, parts.seconds)
    }
}

/**
 * The album's release date at the precision the tags give ("13 October 2024",
 * "October 2024", "2024"), falling back to the stored year; null when unknown.
 */
@Composable
fun albumReleaseDateText(album: Album?): String? {
    val locale = LocalConfiguration.current.locales.get(0)
    return remember(album?.releaseDate, album?.year, locale) {
        album?.let { formatReleaseDate(it.releaseDate, locale) ?: it.year?.toString() }
    }
}
