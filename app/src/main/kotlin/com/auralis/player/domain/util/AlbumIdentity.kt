package com.auralis.player.domain.util

/** Album grouping must use the album-artist tag, never each track's performer. */
fun albumArtistIdentity(albumArtistName: String?): String =
    albumArtistName?.trim()?.takeIf(String::isNotEmpty) ?: "Various Artists"
