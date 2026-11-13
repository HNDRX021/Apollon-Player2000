package com.auralis.player.settings

/**
 * What is needed to put the player back where the user left it after the
 * process is killed: the queue (song ids, in order), the current index and
 * position, and the shuffle/repeat choices.
 */
data class PlaybackSnapshot(
    val songIds: List<Long> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
)
