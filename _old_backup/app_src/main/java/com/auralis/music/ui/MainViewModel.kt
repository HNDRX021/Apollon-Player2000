package com.auralis.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auralis.music.core.AppSettings
import com.auralis.music.core.SettingsRepository
import com.auralis.music.data.AlbumSummary
import com.auralis.music.data.LibraryRepository
import com.auralis.music.data.LibraryStats
import com.auralis.music.data.MusicFolderEntity
import com.auralis.music.data.PlaylistEntity
import com.auralis.music.data.SongEntity
import com.auralis.music.equalizer.EqualizerController
import com.auralis.music.playback.PlaybackManager
import com.auralis.music.playback.PlaybackSnapshot
import com.auralis.music.scanner.MusicScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class MainViewModel(
    private val repository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    val playback: PlaybackManager,
    val scanner: MusicScanner,
    val equalizer: EqualizerController
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        AppSettings("dark", "en", "#D7FF4F", true, true, true, 1f, true, false, 0, emptyList())
    )
    val songs = repository.songs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = repository.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent = repository.recent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentAdded = repository.recentAdded.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mostPlayed = repository.mostPlayed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val albums = repository.albums.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists = repository.artists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val genres = repository.genres.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folders = repository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stats: StateFlow<LibraryStats> = repository.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryStats(0, 0, 0, 0))
    val playbackState: StateFlow<PlaybackSnapshot> = playback.state
    val scanState = scanner.state
    val eqState = equalizer.state

    private val searchQuery = MutableStateFlow("")
    private var sleepJob: Job? = null
    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs
    val searchQueryState: StateFlow<String> = searchQuery
    val searchResults: StateFlow<List<SongEntity>> = searchQuery
        .flatMapLatest { q -> if (q.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(query: String) { searchQuery.value = query }

    fun play(song: SongEntity) = viewModelScope.launch { playback.playSong(song) }
    fun playSongs(songs: List<SongEntity>, index: Int = 0) = viewModelScope.launch { playback.playSongs(songs, index) }
    fun queue(song: SongEntity) = viewModelScope.launch { playback.enqueue(song) }
    fun playNext(song: SongEntity) = viewModelScope.launch { playback.enqueue(song, next = true) }
    fun togglePlayback() = viewModelScope.launch { playback.togglePlayPause() }
    fun pause() = viewModelScope.launch { playback.pause() }
    fun next() = viewModelScope.launch { playback.next() }
    fun moveQueue(from: Int, to: Int) = viewModelScope.launch { playback.moveQueueItem(from, to) }
    fun previous() = viewModelScope.launch { playback.previous() }
    fun seekTo(position: Long) = viewModelScope.launch { playback.seekTo(position) }
    fun toggleShuffle() = viewModelScope.launch { playback.toggleShuffle() }
    fun cycleRepeat() = viewModelScope.launch { playback.cycleRepeat() }
    fun setFavorite(song: SongEntity, favorite: Boolean) = viewModelScope.launch { repository.setFavorite(song.id, favorite) }
    fun createPlaylist(name: String) = viewModelScope.launch { if (name.isNotBlank()) repository.createPlaylist(name) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { repository.deletePlaylist(id) }
    fun replacePlaylistSongs(playlistId: Long, songIds: List<Long>) = viewModelScope.launch { repository.setPlaylistSongs(playlistId, songIds) }

    fun addToPlaylist(playlistId: Long, songIds: List<Long>) = viewModelScope.launch {
        val current = repository.playlistSongs(playlistId).first()
        repository.setPlaylistSongs(playlistId, (current.map { it.id } + songIds).distinct())
    }
    fun removeFromPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch { repository.removeFromPlaylist(playlistId, songId) }
    fun rescan() = viewModelScope.launch { scanner.scanAll() }
    fun scanFolder(folder: MusicFolderEntity) = viewModelScope.launch { scanner.scanFolder(folder) }
    fun addFolder(uri: String, displayName: String) = viewModelScope.launch {
        repository.addFolder(uri, displayName)
        scanner.scanAll()
    }
    fun removeFolder(folder: MusicFolderEntity) = viewModelScope.launch { repository.removeFolder(folder) }

    fun changeTheme(value: String) = viewModelScope.launch { settingsRepository.setTheme(value) }
    fun changeLanguage(value: String) = viewModelScope.launch { settingsRepository.setLanguage(value) }
    fun changeAccent(value: String) = viewModelScope.launch { settingsRepository.setAccent(value) }
    fun setArtworkAnimation(value: Boolean) = viewModelScope.launch { settingsRepository.setArtworkAnimation(value) }
    fun setUiAnimations(value: Boolean) = viewModelScope.launch { settingsRepository.setUiAnimations(value) }
    fun setResumePlayback(value: Boolean) = viewModelScope.launch { settingsRepository.setResumePlayback(value) }
    fun setSpeed(value: Float) = viewModelScope.launch { settingsRepository.setSpeed(value) }

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val duration = minutes.coerceAtLeast(1) * 60_000L
        _sleepRemainingMs.value = duration
        sleepJob = viewModelScope.launch {
            var remaining = duration
            while (remaining > 0L) {
                delay(1_000L)
                remaining = (remaining - 1_000L).coerceAtLeast(0L)
                _sleepRemainingMs.value = remaining
            }
            playback.pause()
            _sleepRemainingMs.value = null
            sleepJob = null
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = null
    }


    override fun onCleared() {
        playback.release()
        equalizer.release()
        super.onCleared()
    }
}

class MainViewModelFactory(private val app: com.auralis.music.AuralisApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(
        app.container.repository,
        app.container.settings,
        app.container.playback,
        app.container.scanner,
        app.container.equalizer
    ) as T
}

