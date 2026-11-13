package com.auralis.music.core

import com.auralis.music.data.SongEntity
import kotlin.math.roundToInt

fun Long.formatDuration(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

fun Int?.formatBitrate(): String = this?.let { "${(it / 1000f).roundToInt()} kbps" } ?: "—"
fun Int?.formatSampleRate(): String = this?.let { "${it / 1000f} kHz" } ?: "—"

fun SongEntity.discTrackLabel(): String = buildString {
    discNumber?.let { append("D$it ") }
    trackNumber?.let { append("${it.toString().padStart(2, '0')}") }
}
