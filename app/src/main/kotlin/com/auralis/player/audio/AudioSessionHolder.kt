package com.auralis.player.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between the playback service (which owns the ExoPlayer and knows
 * its audio session id) and the equalizer (which must attach to that
 * session). Both run in the same process, so a plain singleton suffices.
 * 0 means "no active session".
 */
@Singleton
class AudioSessionHolder @Inject constructor() {
    private val _sessionId = MutableStateFlow(0)
    val sessionId: StateFlow<Int> = _sessionId.asStateFlow()

    fun update(sessionId: Int) {
        _sessionId.value = sessionId
    }
}
