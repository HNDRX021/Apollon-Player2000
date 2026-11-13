package com.auralis.player.playback

import com.auralis.player.domain.model.Song

data class NowPlayingUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffleEnabled: Boolean = false,
    /** One of [androidx.media3.common.Player.REPEAT_MODE_OFF], `_ONE`, `_ALL`. */
    val repeatMode: Int = 0,
    val playbackSpeed: Float = 1f,
    /** Remaining time of a running minute-based sleep timer, or null when none is running. */
    val sleepTimerRemainingMs: Long? = null,
    /** True while "stop after this track" is armed. */
    val sleepAtEndOfTrack: Boolean = false,
) {
    val hasActiveMedia: Boolean get() = currentSong != null
    val isSleepTimerActive: Boolean get() = sleepTimerRemainingMs != null || sleepAtEndOfTrack
}
