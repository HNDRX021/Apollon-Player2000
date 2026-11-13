package com.auralis.player.domain.model

/** Library-wide totals shown in Settings > Storage. */
data class LibraryStats(
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val playlistCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val totalDurationMs: Long = 0L,
)
