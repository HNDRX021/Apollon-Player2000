package com.auralis.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.auralis.player.domain.model.Song
import com.auralis.player.R
import com.auralis.player.domain.repository.MusicRepository
import com.auralis.player.settings.PlaybackSnapshot
import com.auralis.player.settings.SettingsDataStore
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The single source of truth for playback state on the app side. Owns the
 * [MediaController] connection to [PlaybackService] and translates
 * Media3's item/index-based model back into the app's [Song]-based one by
 * keeping the queue it was last given and matching the player's current
 * index into it — the queue isn't re-derived from the player's MediaItems,
 * since those only round-trip a mediaId string, not a full [Song].
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val settingsDataStore: SettingsDataStore,
    private val playbackBridge: PlaybackBridge,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectMutex = Mutex()
    private var controller: MediaController? = null
    private var positionTickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var tickCount = 0
    private var lastErrorSongId: Long? = null
    /**
     * True while an autoplay queue load is in flight. Media3 reports the new queue's first item as a
     * PLAYLIST_CHANGED transition, which can arrive before play() has taken effect (playWhenReady still
     * false); without this flag that first track was treated like a restored, paused queue and never counted.
     */
    @Volatile private var autoPlayLoadInFlight = false
    private var lastRecordedSongId: Long? = null
    private var lastRecordedAtMs = 0L
    @Volatile private var restartTrackOnPrevious = true
    @Volatile private var replayGainEnabled = false
    @Volatile private var replayGainDb = 0f

    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    /** The queue as [Song]s, kept in step with what was last handed to the player. */
    private var currentQueue: List<Song> = emptyList()

    init {
        scope.launch {
            settingsDataStore.settings.collect {
                restartTrackOnPrevious = it.restartTrackOnPrevious
                replayGainEnabled = it.replayGainEnabled
                applyPlaybackVolume()
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMetadata(metadata: Metadata) {
            replayGainDb = if (replayGainEnabled) ReplayGainParser.trackGainDb(metadata) ?: 0f else 0f
            applyPlaybackVolume()
        }

        override fun onPlayerError(error: PlaybackException) {
            val player = controller ?: return
            val failedSong = currentQueue.getOrNull(player.currentMediaItemIndex)
            if (failedSong != null && lastErrorSongId != failedSong.id) {
                lastErrorSongId = failedSong.id
                Toast.makeText(context, context.getString(R.string.playback_error_skipping, failedSong.title), Toast.LENGTH_LONG).show()
            }
            // Continue through the queue so one corrupt or vanished document
            // cannot strand playback on a broken item.
            if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.pause()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            syncStateFromPlayer(player)
            // The position only moves while playing, so it is only polled then (see startPositionTicker).
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying) startPositionTicker()
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                persistSnapshot()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // The queue ran out while "stop after this track" was armed: nothing left to stop.
            if (playbackState == Player.STATE_ENDED && _uiState.value.sleepAtEndOfTrack) cancelSleepTimer()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val player = controller ?: return
            replayGainDb = 0f
            applyPlaybackVolume()
            if (_uiState.value.sleepAtEndOfTrack &&
                (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            ) {
                // The track the user wanted to finish has ended: stop on the
                // start of the next one instead of playing it.
                player.pause()
                player.seekTo(0)
                cancelSleepTimer()
                syncStateFromPlayer(player)
                return
            }
            syncStateFromPlayer(player)
            // A restored (paused) queue loads its first item via a PLAYLIST_CHANGED
            // transition; that is not a play.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && !player.playWhenReady && !autoPlayLoadInFlight) return
            val song = currentQueue.getOrNull(player.currentMediaItemIndex) ?: return
            // The same load can be reported twice (locally, then confirmed by the service): count it once.
            val now = SystemClock.elapsedRealtime()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                song.id == lastRecordedSongId && now - lastRecordedAtMs < DUPLICATE_PLAY_WINDOW_MS
            ) return
            lastRecordedSongId = song.id
            lastRecordedAtMs = now
            scope.launch { repository.recordPlayed(song.id) }
        }
    }

    suspend fun playQueue(songs: List<Song>, startIndex: Int) {
        loadQueue(songs, startIndex, positionMs = 0L, autoPlay = true, requireEmptyPlayer = false)
    }

    /**
     * Replaces the queue with [songs] exactly as given and starts at the first one, with shuffle
     * mode off so playback follows the queue order. Because the queue is replaced rather than added
     * to, calling this repeatedly never piles up duplicate entries. (The caller randomizes the list
     * first when it wants a shuffled queue.)
     */
    suspend fun playInOrder(songs: List<Song>) {
        if (songs.isEmpty()) return
        ensureController().shuffleModeEnabled = false
        loadQueue(songs, 0, positionMs = 0L, autoPlay = true, requireEmptyPlayer = false)
    }

    /** Starts [songs] from a random track with shuffle switched on. */
    suspend fun playShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        val started = loadQueue(songs, songs.indices.random(), positionMs = 0L, autoPlay = true, requireEmptyPlayer = false)
        if (started) {
            controller?.let {
                it.shuffleModeEnabled = true
                syncStateFromPlayer(it)
            }
        }
    }

    /**
     * Puts the last saved queue back in the player, paused, at the saved
     * position. Runs once at app start; does nothing if something is already
     * loaded (activity recreated while the service lived on) or the user has
     * already started playing by the time the saved songs are resolved.
     */
    fun restoreLastSession() {
        scope.launch {
            if (currentQueue.isNotEmpty()) return@launch
            val snapshot = settingsDataStore.playbackSnapshot.first()
            if (snapshot.songIds.isEmpty()) return@launch

            val songs = repository.getSongsByIds(snapshot.songIds)
            if (songs.isEmpty()) return@launch

            // `songs` is an in-order subsequence of the saved ids (vanished
            // songs are dropped); recover each survivor's original index so
            // the saved position can be matched to the right one.
            val originalIndexes = ArrayList<Int>(songs.size)
            var next = 0
            snapshot.songIds.forEachIndexed { index, id ->
                if (next < songs.size && songs[next].id == id) {
                    originalIndexes += index
                    next++
                }
            }
            val start = originalIndexes.indexOfFirst { it >= snapshot.index }.let { if (it == -1) songs.lastIndex else it }
            val savedPositionMs = if (originalIndexes.getOrNull(start) == snapshot.index) snapshot.positionMs else 0L

            val loaded = loadQueue(songs, start, savedPositionMs, autoPlay = false, requireEmptyPlayer = true)
            if (loaded) {
                controller?.let {
                    it.shuffleModeEnabled = snapshot.shuffleEnabled
                    it.repeatMode = snapshot.repeatMode
                    syncStateFromPlayer(it)
                }
            }
        }
    }

    private suspend fun loadQueue(
        songs: List<Song>,
        startIndex: Int,
        positionMs: Long,
        autoPlay: Boolean,
        requireEmptyPlayer: Boolean,
    ): Boolean {
        if (songs.isEmpty()) return false
        val player = ensureController()

        // Resolve each song's URI, keeping only the ones that resolved. The
        // queue the UI sees (currentQueue) must stay index-aligned with the
        // MediaItems handed to the player, so both are built from the same
        // filtered pairs. If the song the user tapped was itself dropped,
        // the start index is remapped to the first surviving song at or
        // after it.
        // Resolved off the main thread: a queue can hold thousands of songs (tapping one in the Library
        // queues the whole list), and building that many MediaItems on the UI thread froze the tap.
        val resolved = withContext(Dispatchers.Default) {
            songs.mapIndexedNotNull { originalIndex, song ->
                repository.resolvePlayableUri(song)?.let { uri ->
                    Triple(originalIndex, song, MediaItemFactory.from(song, uri))
                }
            }
        }
        if (resolved.isEmpty()) return false
        if (requireEmptyPlayer && player.mediaItemCount > 0) return false

        val items = resolved.map { it.third }
        val clampedStartIndex = resolved
            .indexOfFirst { it.first >= startIndex }
            .let { if (it == -1) resolved.lastIndex else it }
        val exactStart = resolved[clampedStartIndex].first == startIndex

        currentQueue = resolved.map { it.second }
        autoPlayLoadInFlight = autoPlay
        player.setMediaItems(items, clampedStartIndex, if (exactStart) positionMs else 0L)
        player.prepare()
        if (autoPlay) player.play()
        autoPlayLoadInFlight = false
        syncStateFromPlayer(player)
        startPositionTicker()
        return true
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() {
        controller?.let { if (it.hasNextMediaItem()) it.seekToNext() }
    }

    fun skipPrevious() {
        controller?.let { player ->
            if (restartTrackOnPrevious && player.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
                player.seekTo(0)
            } else if (player.hasPreviousMediaItem()) {
                // Not seekToPrevious(): Media3 would itself restart the track after 3 s, which would
                // ignore the "restart on previous" setting when it is switched off.
                player.seekToPreviousMediaItem()
            } else if (restartTrackOnPrevious) {
                player.seekTo(0)
            }
        }
    }

    fun toggleShuffle() {
        val player = controller ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        syncStateFromPlayer(player)
    }

    /** Cycles off -> all -> one -> off. */
    fun cycleRepeatMode() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        syncStateFromPlayer(player)
    }

    // --- Playback speed and sleep timer ---------------------------------------

    fun setPlaybackSpeed(speed: Float) {
        val player = controller ?: return
        player.setPlaybackSpeed(speed)
        syncStateFromPlayer(player)
    }

    /** Pauses playback after [minutes]. Replaces any timer already running. */
    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) return
        cancelSleepTimer()
        val endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = minutes * 60_000L)
        sleepTimerJob = scope.launch {
            while (true) {
                val remaining = endAt - SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = remaining)
                delay(1_000)
            }
            controller?.pause()
            _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = null)
            sleepTimerJob = null
        }
    }

    /** Lets the current track finish, then stops instead of starting the next. */
    fun sleepAtEndOfCurrentTrack() {
        cancelSleepTimer()
        playbackBridge.setSleepAtEndOfTrack(true)
        _uiState.value = _uiState.value.copy(sleepAtEndOfTrack = true)
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        playbackBridge.setSleepAtEndOfTrack(false)
        _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = null, sleepAtEndOfTrack = false)
    }

    // --- Queue editing -------------------------------------------------------
    // currentQueue is edited first, then the same edit is sent to the player,
    // so the app-side list and the player's timeline stay index-aligned.

    fun skipToQueueItem(index: Int) {
        val player = controller ?: return
        if (index !in currentQueue.indices) return
        player.seekToDefaultPosition(index)
        player.play()
    }

    /**
     * Adds [song] right after the current one ([playNext]) or at the end of
     * the queue. If nothing is queued yet, the song simply starts playing.
     */
    suspend fun enqueue(song: Song, playNext: Boolean) {
        val player = controller
        if (player == null || currentQueue.isEmpty()) {
            playQueue(listOf(song), 0)
            return
        }
        val uri = repository.resolvePlayableUri(song) ?: return
        val index = if (playNext) (player.currentMediaItemIndex + 1).coerceIn(0, currentQueue.size) else currentQueue.size
        currentQueue = currentQueue.toMutableList().also { it.add(index, song) }
        player.addMediaItem(index, MediaItemFactory.from(song, uri))
        syncStateFromPlayer(player)
    }

    fun clearQueue() {
        val player = controller ?: return
        currentQueue = emptyList()
        player.stop()
        player.clearMediaItems()
        syncStateFromPlayer(player)
        // persistSnapshot() ignores empty queues, so overwrite the saved one explicitly.
        scope.launch(Dispatchers.IO) { settingsDataStore.savePlaybackSnapshot(PlaybackSnapshot()) }
    }

    fun moveQueueItem(from: Int, to: Int) {
        val player = controller ?: return
        if (from !in currentQueue.indices || to !in currentQueue.indices || from == to) return
        currentQueue = currentQueue.toMutableList().also { it.add(to, it.removeAt(from)) }
        player.moveMediaItem(from, to)
        syncStateFromPlayer(player)
    }

    fun removeQueueItem(index: Int) {
        val player = controller ?: return
        if (index !in currentQueue.indices) return
        currentQueue = currentQueue.toMutableList().also { it.removeAt(index) }
        player.removeMediaItem(index)
        syncStateFromPlayer(player)
    }

    /**
     * Called after Edit File changed tags: swaps the fresh [Song] data into the queue so titles, artists and
     * covers update everywhere. The playing item is left alone in the player (replacing it would restart it);
     * its notification text refreshes on the next track change.
     */
    suspend fun refreshSongs(songIds: Set<Long>) {
        if (currentQueue.none { it.id in songIds }) return
        val player = controller ?: return
        val fresh = repository.getSongsByIds(songIds.toList()).associateBy { it.id }
        currentQueue = currentQueue.map { fresh[it.id] ?: it }
        val playing = player.currentMediaItemIndex
        currentQueue.forEachIndexed { index, song ->
            if (index != playing && song.id in songIds && index < player.mediaItemCount) {
                repository.resolvePlayableUri(song)?.let { uri -> player.replaceMediaItem(index, MediaItemFactory.from(song, uri)) }
            }
        }
        syncStateFromPlayer(player)
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        // Reflect the seek immediately rather than waiting for the next
        // ticker tick, so a dragged slider doesn't visibly snap back first.
        _uiState.value = _uiState.value.copy(positionMs = positionMs)
    }

    private suspend fun ensureController(): MediaController = connectMutex.withLock {
        controller?.let { return@withLock it }

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val newController = MediaController.Builder(context, sessionToken)
            .buildAsync()
            .await()
        newController.addListener(playerListener)
        newController.volume = volumeForGain(0f)
        controller = newController
        newController
    }

    private fun syncStateFromPlayer(player: Player) {
        val index = player.currentMediaItemIndex
        val song = currentQueue.getOrNull(index)
        _uiState.value = _uiState.value.copy(
            currentSong = song,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = player.duration.coerceAtLeast(0L),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            queue = currentQueue,
            currentIndex = index,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            playbackSpeed = player.playbackParameters.speed,
        )
    }

    /** Saves the queue and position so playback can be restored after the process dies. */
    private fun persistSnapshot() {
        val player = controller ?: return
        // An empty queue means nothing has been loaded yet (e.g. a freshly
        // started service); never overwrite a good snapshot with that.
        if (currentQueue.isEmpty()) return
        val snapshot = PlaybackSnapshot(
            songIds = currentQueue.map { it.id },
            index = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        scope.launch(Dispatchers.IO) { settingsDataStore.savePlaybackSnapshot(snapshot) }
    }

    /** The periodic checkpoint: only the index and position changed since the last full [persistSnapshot]. */
    private fun persistPosition(player: Player) {
        if (currentQueue.isEmpty()) return
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        scope.launch(Dispatchers.IO) { settingsDataStore.savePlaybackPosition(index, positionMs) }
    }

    /**
     * Publishes the playback position twice a second, but only while something is actually playing: it
     * used to run forever, so every screen that observes the player state (mini player, Now Playing,
     * queue) was recomposed twice a second even while paused or in the background. Pausing, seeking and
     * every other state change publish the position through [syncStateFromPlayer] instead, and
     * IS_PLAYING_CHANGED restarts the loop when playback resumes.
     */
    private fun startPositionTicker() {
        if (positionTickerJob?.isActive == true) return
        positionTickerJob = scope.launch {
            while (isActive) {
                val player = controller ?: break
                if (!player.isPlaying) break
                _uiState.value = _uiState.value.copy(positionMs = player.currentPosition.coerceAtLeast(0L))
                // Checkpoint the position about every 5 s while playing.
                tickCount++
                if (tickCount % 10 == 0) persistPosition(player)
                delay(500)
            }
        }
    }

    private fun applyPlaybackVolume() {
        controller?.volume = volumeForGain((if (replayGainEnabled) replayGainDb else 0f).coerceAtMost(0f))
    }

    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        cont.invokeOnCancellation { cancel(true) }
    }

    private companion object {
        const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
        const val DUPLICATE_PLAY_WINDOW_MS = 2_000L
        fun volumeForGain(db: Float): Float = Math.pow(10.0, db.coerceIn(-36f, 0f).toDouble() / 20.0).toFloat()
    }
}
