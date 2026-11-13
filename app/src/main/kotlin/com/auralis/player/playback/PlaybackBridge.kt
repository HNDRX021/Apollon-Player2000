package com.auralis.player.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny in-process channel from the UI-side [PlayerController] to the
 * [PlaybackService]. The service owns the crossfade engine but can't see the
 * sleep timer, and a "stop after this track" timer must not crossfade into
 * the next track. Both live in the same process, so a singleton is enough.
 */
@Singleton
class PlaybackBridge @Inject constructor() {
    private val _sleepAtEndOfTrack = MutableStateFlow(false)
    val sleepAtEndOfTrack: StateFlow<Boolean> = _sleepAtEndOfTrack.asStateFlow()

    fun setSleepAtEndOfTrack(enabled: Boolean) {
        _sleepAtEndOfTrack.value = enabled
    }
}
