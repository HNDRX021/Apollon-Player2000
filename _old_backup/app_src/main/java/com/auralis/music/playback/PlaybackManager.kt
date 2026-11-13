package com.auralis.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.auralis.music.core.AppSettings
import com.auralis.music.data.LibraryRepository
import com.auralis.music.data.SongEntity
import com.auralis.music.equalizer.EqualizerController
import com.auralis.music.core.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PlaybackManager(
    private val context: Context,
    private val repository: LibraryRepository,
    private val equalizer: EqualizerController,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connectJob: Job? = null
    private var tickerJob: Job? = null
    private var lastPersistedPositionMs: Long = -1L

    private val _state = MutableStateFlow(PlaybackSnapshot())
    val state: StateFlow<PlaybackSnapshot> = _state

    init {
        scope.launch {
            settingsRepository.settings.collect { settings -> applySettings(settings) }
        }
        connect()
    }

    private fun connect() {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            val c = suspendCancellableCoroutine<MediaController> { cont ->
                future.addListener({
                    if (cont.isActive) {
                        runCatching { cont.resume(future.get()) }
                            .onFailure { cont.cancel(it) }
                    }
                }, context.mainExecutor)
                cont.invokeOnCancellation { MediaController.releaseFuture(future) }
            }
            controller = c
            c.addListener(listener)
            startTicker()
            publish()
        }
    }

    @OptIn(UnstableApi::class)
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            scope.launch {
                mediaItem?.mediaId?.toLongOrNull()?.let { repository.registerPlay(it) }
            }
            publish()
        }
        override fun onPlaybackStateChanged(playbackState: Int) = publish()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = publish()
        override fun onRepeatModeChanged(repeatMode: Int) = publish()
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            equalizer.attach(audioSessionId)
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) = publish(error.errorCodeName)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                publish()
                persistPositionIfNeeded()
                delay(250)
            }
        }
    }

    private suspend fun persistPositionIfNeeded() {
        val c = controller ?: return
        val id = c.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        if (!c.isPlaying) return
        val pos = c.currentPosition.coerceAtLeast(0L)
        if (lastPersistedPositionMs < 0 || kotlin.math.abs(pos - lastPersistedPositionMs) >= 5_000L) {
            repository.updatePlaybackPosition(id, pos)
            lastPersistedPositionMs = pos
        }
    }

    private fun publish(error: String? = null) {
        val c = controller ?: return
        _state.value = PlaybackSnapshot(
            isPlaying = c.isPlaying,
            mediaId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
            bufferedPositionMs = c.bufferedPosition.coerceAtLeast(0L),
            currentIndex = c.currentMediaItemIndex,
            queueSize = c.mediaItemCount,
            queueIds = buildList { for (i in 0 until c.mediaItemCount) c.getMediaItemAt(i).mediaId.toLongOrNull()?.let(::add) },
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            error = error
        )
    }

    private suspend fun requireController(): MediaController {
        controller?.let { return it }
        connect()
        while (controller == null) delay(40)
        return controller!!
    }

    suspend fun playSong(song: SongEntity) {
        val c = requireController()
        val settings = settingsRepository.settings.first()
        val startPosition = if (settings.resumePlayback) song.lastPositionMs else 0L
        c.setMediaItem(toMediaItem(song), startPosition)
        c.prepare()
        c.play()
    }

    suspend fun playSongs(songs: List<SongEntity>, startIndex: Int = 0) {
        val c = requireController()
        c.setMediaItems(songs.map(::toMediaItem), startIndex, 0L)
        c.prepare()
        c.play()
    }

    suspend fun enqueue(song: SongEntity, next: Boolean = false) {
        val c = requireController()
        val index = if (next) (c.currentMediaItemIndex + 1).coerceAtLeast(0) else c.mediaItemCount
        c.addMediaItem(index, toMediaItem(song))
        if (c.mediaItemCount == 1) {
            c.prepare()
            c.play()
        }
    }

    suspend fun togglePlayPause() = requireController().let { if (it.isPlaying) it.pause() else it.play() }
    suspend fun pause() = requireController().pause()
    suspend fun next() = requireController().seekToNextMediaItem()
    suspend fun previous() = requireController().seekToPreviousMediaItem()
    suspend fun seekTo(positionMs: Long) = requireController().seekTo(positionMs)
    suspend fun toggleShuffle() { val c = requireController(); c.shuffleModeEnabled = !c.shuffleModeEnabled }
    suspend fun cycleRepeat() {
        val c = requireController()
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    suspend fun removeQueueItem(index: Int) = requireController().removeMediaItem(index)
    suspend fun moveQueueItem(from: Int, to: Int) = requireController().moveMediaItem(from, to)
    suspend fun clearQueue() = requireController().clearMediaItems()

    fun queueIds(): List<Long> = buildList {
        controller?.let { c -> for (i in 0 until c.mediaItemCount) c.getMediaItemAt(i).mediaId.toLongOrNull()?.let(::add) }
    }

    private fun applySettings(settings: AppSettings) {
        controller?.setPlaybackSpeed(settings.speed)
    }

    private fun toMediaItem(song: SongEntity): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.sourceUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .build()
            )
            .build()

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        tickerJob?.cancel()
        connectJob?.cancel()
    }
}

data class PlaybackSnapshot(
    val isPlaying: Boolean = false,
    val mediaId: Long? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val currentIndex: Int = -1,
    val queueSize: Int = 0,
    val queueIds: List<Long> = emptyList(),
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null
)
