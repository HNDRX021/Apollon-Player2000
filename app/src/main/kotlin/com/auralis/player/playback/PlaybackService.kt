package com.auralis.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.auralis.player.MainActivity
import com.auralis.player.audio.AudioSessionHolder
import com.auralis.player.audio.EqualizerParams
import com.auralis.player.settings.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts the single ExoPlayer + MediaSession for the app's lifetime while
 * playback is active. Deliberately thin: it owns the player and answers
 * [onGetSession], but has no repository/database access — [PlayerController]
 * resolves each song's playable URI and builds MediaItems *before* handing
 * them to this service, so the service itself only ever deals in
 * already-resolved MediaItems.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var audioSessionHolder: AudioSessionHolder

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var equalizerParams: EqualizerParams

    @Inject
    lateinit var playbackBridge: PlaybackBridge

    private var mediaSession: MediaSession? = null
    private var crossfadeEngine: CrossfadeEngine? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var crossfadeEnabled = false
    @Volatile private var crossfadeDurationMs = 8_000L
    @Volatile private var pauseOnAudioBecomingNoisy = true
    @Volatile private var replayGainEnabled = false
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var replayGainGainDb = 0f
    private var noisyReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()

        // Declaring the content as music with handleAudioFocus = true makes
        // ExoPlayer pause/duck for calls, other media apps and navigation
        // prompts. Without it playback would simply overlap other audio.
        val musicAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // The renderers factory puts the five-band equalizer into the player's own
        // audio sink (see EqualizerRenderersFactory), so it is applied exactly once
        // and cannot become detached from playback.
        val renderersFactory = EqualizerRenderersFactory(this, equalizerParams)
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(musicAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Follow the audio session for the ReplayGain loudness enhancer if ExoPlayer
        // ever reconfigures the output.
        audioSessionHolder.update(player.audioSessionId)
        attachLoudnessEnhancer(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioSessionHolder.update(audioSessionId)
                attachLoudnessEnhancer(audioSessionId)
            }

            override fun onMetadata(metadata: Metadata) {
                replayGainGainDb = if (replayGainEnabled) ReplayGainParser.trackGainDb(metadata) ?: 0f else 0f
                applyReplayGain()
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                replayGainGainDb = 0f
                applyReplayGain()
            }
        })

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()

        noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && pauseOnAudioBecomingNoisy) {
                    mediaSession?.player?.pause()
                }
            }
        }.also { receiver ->
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }

        serviceScope.launch {
            settingsDataStore.settings.collect {
                crossfadeEnabled = it.crossfadeEnabled
                crossfadeDurationMs = it.crossfadeSeconds * 1_000L
                pauseOnAudioBecomingNoisy = it.pauseOnAudioBecomingNoisy
                replayGainEnabled = it.replayGainEnabled
                if (!replayGainEnabled) replayGainGainDb = 0f
                applyReplayGain()
                crossfadeEngine?.onConfigurationChanged()
            }
        }
        crossfadeEngine = CrossfadeEngine(
            playerA = player,
            context = this,
            renderersFactory = renderersFactory,
            handler = Handler(Looper.getMainLooper()),
            isEnabled = { crossfadeEnabled && !playbackBridge.sleepAtEndOfTrack.value },
            crossfadeDurationMs = { crossfadeDurationMs },
        ).also { it.start() }
        serviceScope.launch {
            playbackBridge.sleepAtEndOfTrack.collect {
                crossfadeEngine?.onConfigurationChanged()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession ?: return
        if (!session.player.playWhenReady || session.player.mediaItemCount == 0) {
            // Nothing actively playing — let the service (and its
            // foreground notification) go away with the swiped-away task.
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        audioSessionHolder.update(0)
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        crossfadeEngine?.release()
        noisyReceiver?.let { runCatching { unregisterReceiver(it) } }
        noisyReceiver = null
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    private fun attachLoudnessEnhancer(audioSessionId: Int) {
        loudnessEnhancer?.release()
        loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
        applyReplayGain()
    }

    private fun applyReplayGain() {
        runCatching {
            loudnessEnhancer?.let { enhancer ->
                val positiveGainMb = if (replayGainEnabled) (replayGainGainDb.coerceAtLeast(0f) * 100f).toInt() else 0
                enhancer.setTargetGain(positiveGainMb)
                enhancer.enabled = positiveGainMb > 0
            }
        }
    }
}
