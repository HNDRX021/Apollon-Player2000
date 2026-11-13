package com.auralis.player.ui.screens.settings

import java.util.Locale

/**
 * Pure helpers for hex colour input in the custom accent picker (no Android dependencies, so they are
 * unit-testable). Colours are ARGB packed in a Long, the way the accent is stored in DataStore.
 */

/** Forces the colour to be fully opaque: an accent never carries transparency. */
fun opaqueAccent(argb: Long): Long = (argb and 0xFFFFFFL) or 0xFF000000L

/** "7C5CFC" for 0xFF7C5CFC; always six upper-case hex digits, no leading '#'. */
fun formatHexColor(argb: Long): String = String.format(Locale.ROOT, "%06X", argb and 0xFFFFFFL)

/** Parses "7C5CFC" or "#7c5cfc" into an opaque ARGB colour; null unless it is exactly six hex digits. */
fun parseHexColor(input: String): Long? {
    val digits = input.trim().removePrefix("#")
    if (digits.length != 6) return null
    if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return opaqueAccent(digits.toLong(16))
}

/** Keeps only hex digits, upper-cased and capped at six, for live typing in the hex field. */
fun sanitizeHexInput(input: String): String =
    input.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.take(6).uppercase(Locale.ROOT)
