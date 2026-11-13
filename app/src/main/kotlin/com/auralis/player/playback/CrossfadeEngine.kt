package com.auralis.player.playback

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory

/**
 * Crossfades automatic track transitions on [playerA] — the single
 * ExoPlayer bound to the app's [PlaybackService] MediaSession, which
 * [PlayerController]/MediaController/the UI already know how to drive.
 * Nothing outside this file needs to know crossfade exists.
 *
 * How it works. A private "shadow" player ([playerB]) is handed the same
 * upcoming MediaItem and starts it early, ramped in from silence while
 * [playerA] ramps out ([Phase.FADING]). [playerA]'s own playlist is never
 * touched, so when it reaches the end of the outgoing track it auto-advances
 * to that same next item, already silent. Then:
 *
 *  1. [Phase.WAITING_READY] — A is seeked to where the shadow has reached and
 *     stays muted while the shadow keeps playing at full volume, so there is
 *     never a silent gap, however long the seek takes.
 *  2. [Phase.RAMPING] — once A reports READY, a short (250 ms) ramp hands the
 *     audio from the shadow to A. The brief overlap masks the few hundred
 *     milliseconds of drift between the two players.
 *
 * The fade is driven by how much of the outgoing track is actually left, not
 * by a wall-clock timer, so a pause/seek/late start can't leave the volumes
 * out of step with the audio.
 *
 * Anything that invalidates the plan cancels the crossfade and restores A:
 * a manual skip or repeat transition, a seek, a pause, a queue edit, a shuffle
 * or repeat change, the feature being switched off, or the shadow erroring.
 *
 * Equalizer: the shadow player is built with the same [renderersFactory] as
 * [playerA], so its audio sink carries its own equalizer processor following the
 * same shared settings, and the overlap is equalized like the rest of playback.
 */
class CrossfadeEngine(
    private val playerA: ExoPlayer,
    private val context: Context,
    private val renderersFactory: RenderersFactory,
    private val handler: Handler,
    private val isEnabled: () -> Boolean,
    private val crossfadeDurationMs: () -> Long = { DEFAULT_CROSSFADE_MS },
) {
    private enum class Phase { IDLE, FADING, WAITING_READY, RAMPING }

    private var playerB: ExoPlayer? = null
    private var phase = Phase.IDLE
    private var phaseStartMs = 0L
    private var fadeLengthMs = DEFAULT_CROSSFADE_MS
    private var shadowMediaId: String? = null
    private var started = false
    private var tickScheduled = false

    private val listenerA = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            when {
                phase == Phase.IDLE -> Unit
                reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> cancelCrossfade()
                phase == Phase.FADING && mediaItem?.mediaId == shadowMediaId -> beginHandoff()
                else -> cancelCrossfade() // queue changed under us; the shadow is playing the wrong track
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // Our own handoff seek happens outside FADING, so it is not caught here.
            if (phase == Phase.FADING &&
                (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)
            ) {
                cancelCrossfade()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && phase != Phase.IDLE) cancelCrossfade()
            updateTickSchedule()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (phase == Phase.FADING) cancelCrossfade()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (phase == Phase.FADING) cancelCrossfade()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            if (phase == Phase.FADING) cancelCrossfade()
        }
    }

    private val listenerB = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            // The next track can't be read: let A finish normally instead of
            // fading into silence.
            if (phase == Phase.FADING) cancelCrossfade()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            tickScheduled = false
            if (!started) return
            tickOnce()
            scheduleTick(TICK_INTERVAL_MS)
        }
    }

    fun start() {
        if (started) return
        started = true
        playerA.addListener(listenerA)
        updateTickSchedule()
    }

    /** Re-check playback scheduling after crossfade or sleep-timer settings change. */
    fun onConfigurationChanged() {
        handler.post {
            if (started) updateTickSchedule()
        }
    }

    fun release() {
        started = false
        stopTick()
        playerA.removeListener(listenerA)
        playerB?.removeListener(listenerB)
        playerB?.release()
        playerB = null
        phase = Phase.IDLE
    }

    private fun updateTickSchedule() {
        if (!started || !playerA.playWhenReady || !isEnabled()) {
            if (!isEnabled() && phase != Phase.IDLE) cancelCrossfade()
            stopTick()
            return
        }
        scheduleTick()
    }

    private fun scheduleTick(delayMs: Long = 0L) {
        if (tickScheduled || !started || !playerA.playWhenReady || !isEnabled()) return
        tickScheduled = true
        if (delayMs == 0L) handler.post(tick) else handler.postDelayed(tick, delayMs)
    }

    private fun stopTick() {
        handler.removeCallbacks(tick)
        tickScheduled = false
    }

    private fun tickOnce() {
        if (!isEnabled()) {
            if (phase != Phase.IDLE) cancelCrossfade()
            return
        }
        when (phase) {
            Phase.IDLE -> maybeBeginCrossfade()
            Phase.FADING -> {
                val remaining = (playerA.duration - playerA.currentPosition).coerceAtLeast(0L)
                val progress = (1f - remaining.toFloat() / fadeLengthMs).coerceIn(0f, 1f)
                playerA.volume = 1f - progress
                playerB?.volume = progress
            }
            Phase.WAITING_READY -> {
                val waited = SystemClock.elapsedRealtime() - phaseStartMs
                val ready = playerA.playbackState == Player.STATE_READY
                if ((ready && waited >= MIN_HANDOFF_WAIT_MS) || waited >= HANDOFF_TIMEOUT_MS) {
                    phase = Phase.RAMPING
                    phaseStartMs = SystemClock.elapsedRealtime()
                }
            }
            Phase.RAMPING -> {
                val progress = ((SystemClock.elapsedRealtime() - phaseStartMs).toFloat() / HANDOFF_RAMP_MS)
                    .coerceIn(0f, 1f)
                playerA.volume = progress
                playerB?.volume = 1f - progress
                if (progress >= 1f) finishHandoff()
            }
        }
    }

    private fun maybeBeginCrossfade() {
        if (!playerA.isPlaying) return
        if (playerA.repeatMode == Player.REPEAT_MODE_ONE) return // the next track is the same track
        if (!playerA.hasNextMediaItem()) return
        val duration = playerA.duration
        if (duration == C.TIME_UNSET || duration <= 0) return

        // Short tracks get a proportionally shorter fade instead of none.
        val fadeLength = minOf(crossfadeDurationMs(), duration / 2)
        if (fadeLength < MIN_FADE_MS) return
        val remaining = duration - playerA.currentPosition
        if (remaining > fadeLength) return

        val nextIndex = playerA.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val nextItem = playerA.getMediaItemAt(nextIndex)

        val shadow = playerB ?: createShadowPlayer().also { playerB = it }
        shadow.setMediaItem(nextItem)
        shadow.volume = 0f
        shadow.prepare()
        shadow.play()

        shadowMediaId = nextItem.mediaId
        fadeLengthMs = fadeLength
        phase = Phase.FADING
        phaseStartMs = SystemClock.elapsedRealtime()
    }

    private fun createShadowPlayer(): ExoPlayer {
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(context, renderersFactory)
            // Focus stays with playerA; two players both claiming it would pause each other.
            .setAudioAttributes(attributes, /* handleAudioFocus = */ false)
            .build()
            .also { it.addListener(listenerB) }
    }

    /** A just auto-advanced (silent). Mute it, line it up with the shadow, and wait until it can play. */
    private fun beginHandoff() {
        val shadow = playerB
        if (shadow == null) {
            cancelCrossfade()
            return
        }
        playerA.volume = 0f
        shadow.volume = 1f
        phase = Phase.WAITING_READY
        phaseStartMs = SystemClock.elapsedRealtime()
        playerA.seekTo(shadow.currentPosition)
    }

    private fun finishHandoff() {
        stopShadow()
        playerA.volume = 1f
        phase = Phase.IDLE
    }

    private fun cancelCrossfade() {
        stopShadow()
        playerA.volume = 1f
        phase = Phase.IDLE
    }

    private fun stopShadow() {
        playerB?.let {
            it.pause()
            it.stop()
            it.clearMediaItems()
        }
        shadowMediaId = null
    }

    companion object {
        private const val DEFAULT_CROSSFADE_MS = 8_000L
        private const val MIN_FADE_MS = 1_000L
        private const val TICK_INTERVAL_MS = 100L
        private const val MIN_HANDOFF_WAIT_MS = 120L
        private const val HANDOFF_TIMEOUT_MS = 2_000L
        private const val HANDOFF_RAMP_MS = 250L
    }
}
