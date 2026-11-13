package com.auralis.player.ui.screens

import androidx.lifecycle.SavedStateHandle
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.player.audio.EqualizerController
import com.auralis.player.domain.model.Album
import com.auralis.player.domain.model.Artist
import com.auralis.player.domain.model.DailyMix
import com.auralis.player.domain.model.Song
import com.auralis.player.data.lyrics.LyricsLookup
import com.auralis.player.data.lyrics.LyricsLookupResult
import com.auralis.player.domain.repository.MusicRepository
import com.auralis.player.domain.util.DAILY_ALBUM_COUNT
import com.auralis.player.domain.util.DAILY_ALBUM_SALT
import com.auralis.player.domain.util.DAILY_SONG_COUNT
import com.auralis.player.domain.util.DAILY_SONG_SALT
import com.auralis.player.domain.util.AlbumSignals
import com.auralis.player.domain.util.albumTrackOrder
import com.auralis.player.domain.util.albumToFavoritesMessage
import com.auralis.player.domain.util.albumToPlaylistMessage
import com.auralis.player.domain.util.FAVORITE_WINDOW_DAYS
import com.auralis.player.domain.util.HOME_FAVORITE_ALBUMS
import com.auralis.player.domain.util.HOME_RECENT_HISTORY_SONGS
import com.auralis.player.domain.util.HOME_RECENT_ITEMS
import com.auralis.player.domain.util.TODAYS_MUSIC_PAGE_SIZE
import com.auralis.player.domain.util.orderedDailyIds
import com.auralis.player.domain.util.pickDailyIds
import com.auralis.player.domain.util.rankFavoriteAlbumIds
import com.auralis.player.domain.util.recentAlbumIds
import com.auralis.player.playback.PlayerController
import com.auralis.player.settings.DailyPicks
import com.auralis.player.settings.SettingsDataStore
import com.auralis.player.settings.ThemeMode
import com.auralis.player.settings.SongSort
import com.auralis.player.settings.AlbumSort
import com.auralis.player.settings.ArtistSort
import com.auralis.player.settings.DuplicateHandlingMode
import com.auralis.player.ui.screens.playlists.PlaylistLibrarySort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class MusicScreensViewModel @Inject constructor(
    val repository: MusicRepository,
    val player: PlayerController,
    val equalizer: EqualizerController,
    val settings: SettingsDataStore,
    private val lyricsLookup: LyricsLookup,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    // The library lists are shared inside this view model: Home reads them from several flows (favorite
    // albums, today's picks, its own lists), and each of those used to start its own copy of the same
    // database query and row mapping. A collector that arrives later gets the latest list straight away.
    private val shareWhileObserved = SharingStarted.WhileSubscribed(5_000)
    val allSongs = repository.observeAllSongs().shareIn(viewModelScope, shareWhileObserved, replay = 1)
    val albums = repository.observeAlbums().shareIn(viewModelScope, shareWhileObserved, replay = 1)
    val recentAlbums = repository.observeRecentAlbums(12)
    val artists = repository.observeArtists().shareIn(viewModelScope, shareWhileObserved, replay = 1)
    val playlists = repository.observePlaylists()
    val folders = repository.observeFolders()
    val stats = repository.observeLibraryStats()
    val favorites = repository.observeFavoriteSongs(50)
    val recentlyPlayed = repository.observeRecentlyPlayedSongs(12)
    val recentlyAdded = repository.observeRecentlyAddedSongs(12)
    val mostPlayed = repository.observeMostPlayedSongs(12)

    // The three automatic playlists, uncapped: the Playlists tab and their pages show every track.
    // They read the same song rows as the library (no copies) and re-emit whenever a like or a play changes them.
    val favoriteSongsAll = repository.observeFavoriteSongs(Int.MAX_VALUE)
    val recentlyPlayedAll = repository.observeRecentlyPlayedSongs(Int.MAX_VALUE)
    val mostPlayedAll = repository.observeMostPlayedSongs(Int.MAX_VALUE)

    /** The Playlists tab's sort, kept in DataStore so it survives leaving the tab and restarting the app. */
    val playlistLibrarySort: Flow<PlaylistLibrarySort> = settings.playlistLibrarySort.map { saved ->
        PlaylistLibrarySort.entries.firstOrNull { it.name == saved } ?: PlaylistLibrarySort.RECENTS
    }
    fun setPlaylistLibrarySort(sort: PlaylistLibrarySort) { viewModelScope.launch { settings.setPlaylistLibrarySort(sort.name) } }
    val scanProgress = repository.scanProgress
    val scanOutcome = repository.scanOutcome
    val playerState = player.uiState
    val equalizerState = equalizer.uiState
    val appSettings = settings.settings
    val searchHistory = settings.searchHistory

    // --- Home screen ---------------------------------------------------------

    /** Newest-first play history, one entry per song; Home folds it into albums for "Recently Played". */
    val recentHistory = repository.observeRecentlyPlayedSongs(HOME_RECENT_HISTORY_SONGS)

    private val _todaysMusicVisible = MutableStateFlow(TODAYS_MUSIC_PAGE_SIZE)

    /**
     * How many of "Today's Music" Home shows: one page at first, one more page per "Show More".
     * Kept here (not in the composable) so it survives rotation and theme changes while Home stays
     * on screen; Home resets it with [resetTodaysMusicPaging] when the user leaves the page.
     */
    val todaysMusicVisible: StateFlow<Int> = _todaysMusicVisible.asStateFlow()

    fun showMoreTodaysMusic() { _todaysMusicVisible.update { it + TODAYS_MUSIC_PAGE_SIZE } }

    /** Back to the first page. Only this count changes: the library, favorites, playlists and queue are untouched. */
    fun resetTodaysMusicPaging() { _todaysMusicVisible.value = TODAYS_MUSIC_PAGE_SIZE }

    /** The local calendar date (`yyyy-MM-dd`); emits again when midnight passes while the app is open. */
    private val dayKey: Flow<String> = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now.toLocalDate().toString())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, nextMidnight).toMillis() + 1_000L)
        }
    }.distinctUntilChanged()

    /**
     * "Your Favorites": up to [HOME_FAVORITE_ALBUMS] albums ranked by a combined preference score
     * (see [rankFavoriteAlbumIds]) built from the live favorites, playlists and play history:
     * the album being a favorite, how many of its songs are favorites, how many of its songs are
     * in playlists (each song once), and recent and overall plays. Recomputed whenever any of those
     * change. Empty when nothing qualifies.
     */
    val favoriteAlbums: Flow<List<Album>> = combine(
        albums,
        allSongs,
        repository.observeSavedSongIds(),
        dayKey.flatMapLatest {
            repository.observeAlbumRecentPlays(System.currentTimeMillis() - FAVORITE_WINDOW_DAYS * 86_400_000L)
        },
    ) { albumList, songs, savedSongIds, recentPlays ->
        val eligible = albumList.filter { it.trackCount > 0 }.associateBy { it.id }
        val saved = savedSongIds.toHashSet()
        val songsByAlbum = songs.groupBy { it.albumId }
        val recentByAlbum = recentPlays.associateBy { it.albumId }
        val signals = eligible.values.map { album ->
            val tracks = songsByAlbum[album.id].orEmpty()
            val recent = recentByAlbum[album.id]
            AlbumSignals(
                albumId = album.id,
                isAlbumFavorite = album.isFavorite,
                favoriteSongs = tracks.count { it.isFavorite },
                playlistSongs = tracks.count { it.id in saved },
                recentPlays = recent?.plays ?: 0,
                recentDays = recent?.days ?: 0,
                totalPlays = tracks.sumOf { it.playCount },
                lastPlayedAt = tracks.maxOfOrNull { it.lastPlayedAt ?: 0L } ?: 0L,
            )
        }
        rankFavoriteAlbumIds(signals, HOME_FAVORITE_ALBUMS).mapNotNull(eligible::get)
    }.flowOn(Dispatchers.Default)

    /**
     * "Today's Suggestions" albums and "Today's Music" songs. The albums and the day's first page of
     * songs are picked at random once per local calendar day and saved, so they stay put when Home
     * is reopened or redrawn and only change when the date does. [DailyMix.songs] is the whole
     * library for the day: that first page, then every other song in a random order that is also
     * fixed for the day, which is what "Show More" pages through.
     * Nothing is picked while a scan is running (the library is still incomplete) or while the
     * library is empty; a pick that has lost items (a folder was removed) or that a growing
     * library could now fill further is redone.
     */
    val dailyMix: Flow<DailyMix> = combine(
        combine(allSongs, albums, recentHistory) { songs, albumList, history -> Triple(songs, albumList, history) },
        settings.dailyPicks,
        dayKey,
        scanProgress,
    ) { library, stored, today, scan ->
        DailyInputs(library.first, library.second, library.third, stored, today, scan.isScanning)
    }.transform { input ->
        val songsById = input.songs.associateBy { it.id }
        val albumsById = input.albums.filter { it.trackCount > 0 }.associateBy { it.id }
        val wantAlbums = minOf(DAILY_ALBUM_COUNT, albumsById.size)
        val wantSongs = minOf(DAILY_SONG_COUNT, songsById.size)
        val storedToday = input.stored.takeIf { it.dayKey == input.today }
        val albumPicks = storedToday?.albumIds.orEmpty().mapNotNull(albumsById::get)
        val firstPage = storedToday?.songIds.orEmpty().distinct().mapNotNull(songsById::get)
        val complete = storedToday != null && albumPicks.size >= wantAlbums && firstPage.size >= wantSongs
        if (complete || input.scanning || songsById.isEmpty()) {
            // Until the day's first page has been picked nothing is shown, so a half-scanned
            // library cannot flash a first page that is then replaced.
            val songs = if (storedToday == null) {
                emptyList<Song>()
            } else {
                orderedDailyIds(firstPage.map { it.id }, songsById.keys, input.today).mapNotNull(songsById::get)
            }
            emit(DailyMix(albumPicks, songs))
        } else {
            // Saving makes the stored picks emit again, which then lands in the branch above.
            settings.saveDailyPicks(
                DailyPicks(
                    dayKey = input.today,
                    albumIds = pickDailyIds(
                        candidates = albumsById.keys,
                        count = DAILY_ALBUM_COUNT,
                        dayKey = input.today,
                        salt = DAILY_ALBUM_SALT,
                        avoid = recentAlbumIds(input.history, HOME_RECENT_ITEMS).toSet(),
                    ),
                    songIds = pickDailyIds(
                        candidates = songsById.keys,
                        count = DAILY_SONG_COUNT,
                        dayKey = input.today,
                        salt = DAILY_SONG_SALT,
                    ),
                ),
            )
        }
    }.flowOn(Dispatchers.Default)

    val albumId: Long = savedStateHandle.get<Long>(AlbumDetailViewModel.ALBUM_ID_ARG) ?: -1L
    val artistId: Long = savedStateHandle.get<Long>(ArtistDetailViewModel.ARTIST_ID_ARG) ?: -1L
    val playlistId: Long = savedStateHandle.get<Long>(PlaylistDetailViewModel.PLAYLIST_ID_ARG) ?: -1L

    val album = repository.observeAlbum(albumId)
    val albumSongs = repository.observeAlbumSongs(albumId)
    val artist = repository.observeArtist(artistId)
    val artistSongs = repository.observeArtistSongs(artistId)
    val artistAlbums = repository.observeArtistAlbums(artistId)
    val playlist = repository.observePlaylist(playlistId)
    val playlistEntries = repository.observePlaylistEntries(playlistId)

    private val searchQuery = MutableStateFlow("")
    // A blank query never shows results (Search shows its history instead), so it skips the database:
    // LIKE '%%' matched everything and loaded up to 200 songs, 50 albums and every artist whenever the
    // screen was opened or cleared.
    val searchSongs = searchQuery.flatMapLatest { if (it.isBlank()) flowOf(emptyList<Song>()) else repository.searchSongs(it) }
    val searchAlbums = searchQuery.flatMapLatest { if (it.isBlank()) flowOf(emptyList<Album>()) else repository.searchAlbums(it) }
    val searchArtists = searchQuery.flatMapLatest { if (it.isBlank()) flowOf(emptyList<Artist>()) else repository.searchArtists(it) }

    fun search(query: String) { searchQuery.value = query }
    fun rememberSearch(query: String) { viewModelScope.launch { settings.rememberSearch(query) } }
    fun clearSearchHistory() { viewModelScope.launch { settings.clearSearchHistory() } }
    fun play(songs: List<Song>, index: Int = 0) { viewModelScope.launch { player.playQueue(songs, index) } }
    /** Replaces the queue with [songs] in the given order (shuffle mode off) and starts playing. */
    fun playInOrder(songs: List<Song>) { viewModelScope.launch { player.playInOrder(songs) } }
    /** Replaces the queue with [songs] in a freshly randomized order and starts playing the first one. */
    fun playRandomized(songs: List<Song>) { viewModelScope.launch { player.playInOrder(songs.shuffled()) } }
    fun shuffle(songs: List<Song>) { viewModelScope.launch { player.playShuffled(songs) } }
    fun playNext(song: Song) { viewModelScope.launch { player.enqueue(song, playNext = true) } }
    fun addToQueue(song: Song) { viewModelScope.launch { player.enqueue(song, playNext = false) } }
    /** Appends [songs] to the end of the queue, in order. */
    fun addAllToQueue(songs: List<Song>) { viewModelScope.launch { songs.forEach { player.enqueue(it, playNext = false) } } }
    /** Inserts [songs] right after the current track, keeping their order. With an empty queue they simply start playing, in order. */
    fun playAllNext(songs: List<Song>) {
        viewModelScope.launch {
            // enqueue() starts a song on its own when nothing is queued, which would put the last of
            // [songs] first when inserting in reverse; start the whole list in order instead.
            if (player.uiState.value.queue.isEmpty()) player.playQueue(songs, 0)
            else songs.asReversed().forEach { player.enqueue(it, playNext = true) }
        }
    }
    /** The tracks of [albumId] in track order, read once. The album menus only know the album, not its songs. */
    private suspend fun albumTracks(albumId: Long): List<Song> =
        repository.observeAlbumSongs(albumId).first().sortedWith(albumTrackOrder)
    /** Album menu: appends the whole album to the end of the queue. */
    fun addAlbumToQueue(albumId: Long) { viewModelScope.launch { addAllToQueue(albumTracks(albumId)) } }
    /** Album menu: plays the whole album next, in order (the existing play-next queue logic). */
    fun playAlbumNext(albumId: Long) { viewModelScope.launch { playAllNext(albumTracks(albumId)) } }
    /**
     * Album menus: adds the whole album, in track order, to the END of the playlist [targetPlaylistId]. Songs the
     * playlist already holds are skipped (no duplicates) and its existing entries keep their order; one toast
     * says how many were added.
     */
    fun addAlbumToPlaylist(albumId: Long, targetPlaylistId: Long, playlistName: String) {
        viewModelScope.launch {
            val songIds = albumTracks(albumId).map { it.id }.distinct()
            if (songIds.isEmpty()) {
                Toast.makeText(context, "This album has no available songs.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val added = repository.addSongsToPlaylistSkippingDuplicates(targetPlaylistId, songIds)
            Toast.makeText(context, albumToPlaylistMessage(added, songIds.size, playlistName), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Album Plus menu: favorites every track of the album (tracks that already are favorites are left exactly as
     * they were, so nothing is duplicated or reordered) and marks the album itself as a favorite, which is what
     * the Plus button's check mark and Home's "Your Favorites" read.
     */
    fun addAlbumToFavorites(albumId: Long) {
        viewModelScope.launch {
            val songIds = albumTracks(albumId).map { it.id }.distinct()
            if (songIds.isEmpty()) {
                Toast.makeText(context, "This album has no available songs.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val added = repository.favoriteSongs(songIds)
            repository.setAlbumFavorite(albumId, true)
            Toast.makeText(context, albumToFavoritesMessage(added, songIds.size), Toast.LENGTH_SHORT).show()
        }
    }
    fun favorite(song: Song) { viewModelScope.launch { repository.setFavorite(song.id, !song.isFavorite) } }

    fun saveLyrics(songId: Long, lyrics: String?) {
        viewModelScope.launch { repository.setLyrics(songId, lyrics) }
    }

    suspend fun findLyrics(song: Song): LyricsLookupResult = lyricsLookup.find(song).also { result ->
        if (result is LyricsLookupResult.Found) repository.setLyrics(song.id, result.lyrics)
    }

    fun refreshSongMetadata(song: Song) {
        viewModelScope.launch {
            val refreshed = runCatching { repository.refreshSongMetadata(song.id) }.getOrDefault(false)
            Toast.makeText(context, context.getString(if (refreshed) com.auralis.player.R.string.song_metadata_refreshed else com.auralis.player.R.string.song_metadata_refresh_failed), Toast.LENGTH_SHORT).show()
        }
    }
    fun openSongFolder(song: Song) {
        viewModelScope.launch {
            val uri = repository.resolveParentFolderUri(song)
            if (uri == null) {
                Toast.makeText(context, com.auralis.player.R.string.song_folder_unavailable, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ContextCompat.startActivity(context, intent, null) }
                .onFailure { Toast.makeText(context, com.auralis.player.R.string.song_folder_unavailable, Toast.LENGTH_SHORT).show() }
        }
    }
    fun favoriteAlbum(id: Long, currentlyFavorite: Boolean) { viewModelScope.launch { repository.setAlbumFavorite(id, !currentlyFavorite) } }
    fun favoriteArtist(id: Long, currentlyFavorite: Boolean) { viewModelScope.launch { repository.setArtistFavorite(id, !currentlyFavorite) } }
    fun createPlaylist(name: String) { viewModelScope.launch { repository.createPlaylist(name) } }
    fun renamePlaylist(name: String) { viewModelScope.launch { repository.renamePlaylist(playlistId, name) } }
    fun renamePlaylist(id: Long, name: String) { viewModelScope.launch { repository.renamePlaylist(id, name) } }
    fun addSongToPlaylist(songId: Long) { viewModelScope.launch { repository.addSongToPlaylist(playlistId, songId) } }
    /** Adds [songId] to the playlist [targetPlaylistId] (named [playlistName] in the confirmation) from anywhere in the app. */
    fun addSongToPlaylist(targetPlaylistId: Long, songId: Long, playlistName: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(targetPlaylistId, songId)
            Toast.makeText(context, "Added to $playlistName", Toast.LENGTH_SHORT).show()
        }
    }
    /** Adds all of [songIds] (in order) to the playlist [targetPlaylistId] and confirms once with a toast. */
    fun addSongsToPlaylist(targetPlaylistId: Long, songIds: List<Long>, playlistName: String) {
        if (songIds.isEmpty()) return
        viewModelScope.launch {
            songIds.forEach { repository.addSongToPlaylist(targetPlaylistId, it) }
            val message = if (songIds.size == 1) "Added to $playlistName" else "Added ${songIds.size} songs to $playlistName"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    fun deletePlaylist() { viewModelScope.launch { repository.deletePlaylist(playlistId) } }
    fun deletePlaylist(id: Long) { viewModelScope.launch { repository.deletePlaylist(id) } }

    // --- Playlist section ----------------------------------------------------

    /** Creates a playlist named [name] and hands its id to [onCreated] (not called for a blank name). */
    fun createPlaylistAndOpen(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            if (id > 0) onCreated(id)
        }
    }

    /** Saves the order the user arranged the open playlist in; [orderedEntryIds] are entry ids (not song ids), first to last. */
    fun reorderPlaylist(orderedEntryIds: List<Long>) {
        viewModelScope.launch { repository.reorderPlaylist(playlistId, orderedEntryIds) }
    }

    /** The songs of playlist [id] in their stored order, read once. */
    private suspend fun playlistSongs(id: Long): List<Song> =
        repository.observePlaylistEntries(id).first().map { it.song }

    /** Plays playlist [id] from its first track, or shuffled. Does nothing for an empty playlist. */
    fun playPlaylist(id: Long, shuffled: Boolean) {
        viewModelScope.launch {
            val songs = playlistSongs(id)
            if (songs.isEmpty()) return@launch
            if (shuffled) player.playShuffled(songs) else player.playQueue(songs, 0)
        }
    }

    /** Inserts playlist [id] right after the current track, in order (the existing play-next queue logic). */
    fun playPlaylistNext(id: Long) {
        viewModelScope.launch {
            val songs = playlistSongs(id)
            if (songs.isNotEmpty()) playAllNext(songs)
        }
    }

    /** Appends playlist [id] to the end of the queue, in order. */
    fun addPlaylistToQueue(id: Long) {
        viewModelScope.launch {
            val songs = playlistSongs(id)
            if (songs.isNotEmpty()) addAllToQueue(songs)
        }
    }
    fun removePlaylistEntry(entryId: Long) { viewModelScope.launch { repository.removePlaylistEntry(entryId) } }
    fun addFolder(uri: String, name: String) { viewModelScope.launch { repository.addFolderAndScan(uri, name) } }
    fun setFolderEnabled(id: Long, enabled: Boolean) { viewModelScope.launch { repository.setFolderEnabled(id, enabled) } }
    fun removeFolder(id: Long) { viewModelScope.launch { repository.removeFolder(id) } }
    fun rescanFolder(id: Long) { viewModelScope.launch { repository.rescanFolder(id) } }
    fun refreshLibrary() { viewModelScope.launch { repository.refreshLibrary() } }
    fun cancelRefresh() { repository.cancelRefresh() }
    fun renewFolderAccess(id: Long, uri: String, name: String) { viewModelScope.launch { repository.renewFolderAccess(id, uri, name) } }
    fun cancelFolderScan(id: Long) { repository.cancelFolderScan(id) }
    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }
    fun setBackgroundImageUri(uri: String?) { viewModelScope.launch { settings.setBackgroundImageUri(uri) } }
    fun setAccentColor(color: Long) { viewModelScope.launch { settings.setAccentColor(color) } }
    fun setLanguageTag(tag: String) { viewModelScope.launch { settings.setLanguageTag(tag) } }
    fun setCompactMode(enabled: Boolean) { viewModelScope.launch { settings.setCompactMode(enabled) } }
    fun setRespectNoMedia(enabled: Boolean) { viewModelScope.launch { settings.setRespectNoMedia(enabled) } }
    fun setPauseOnAudioBecomingNoisy(enabled: Boolean) { viewModelScope.launch { settings.setPauseOnAudioBecomingNoisy(enabled) } }
    fun setReplayGainEnabled(enabled: Boolean) { viewModelScope.launch { settings.setReplayGainEnabled(enabled) } }
    fun setDynamicColorEnabled(enabled: Boolean) { viewModelScope.launch { settings.setDynamicColorEnabled(enabled) } }
    fun setDefaultSleepTimerMinutes(minutes: Int) { viewModelScope.launch { settings.setDefaultSleepTimerMinutes(minutes) } }
    fun setSongSort(sort: SongSort) { viewModelScope.launch { settings.setSongSort(sort) } }
    fun setAlbumSort(sort: AlbumSort) { viewModelScope.launch { settings.setAlbumSort(sort) } }
    fun setArtistSort(sort: ArtistSort) { viewModelScope.launch { settings.setArtistSort(sort) } }
    fun setAudioFormatEnabled(extension: String, enabled: Boolean) { viewModelScope.launch { settings.setAudioFormatEnabled(extension, enabled) } }
    fun setDuplicateHandlingMode(mode: DuplicateHandlingMode) { viewModelScope.launch { settings.setDuplicateHandlingMode(mode) } }
    fun setDeepDuplicateScan(enabled: Boolean) { viewModelScope.launch { settings.setDeepDuplicateScan(enabled) } }
    fun setCrossfadeEnabled(enabled: Boolean) { viewModelScope.launch { settings.setCrossfadeEnabled(enabled) } }
    fun setCrossfadeSeconds(seconds: Int) { viewModelScope.launch { settings.setCrossfadeSeconds(seconds) } }
    fun setRestartTrackOnPrevious(enabled: Boolean) { viewModelScope.launch { settings.setRestartTrackOnPrevious(enabled) } }
    fun setShowRemainingTime(enabled: Boolean) { viewModelScope.launch { settings.setShowRemainingTime(enabled) } }

    /** Sets the open playlist's own cover from cropped JPEG bytes, or removes it (null) to go back to the automatic one. */
    fun setPlaylistArtwork(jpeg: ByteArray?) {
        viewModelScope.launch {
            val ok = runCatching { repository.setPlaylistArtwork(playlistId, jpeg) }.getOrDefault(false)
            if (!ok) Toast.makeText(context, "That image couldn't be used as a playlist cover.", Toast.LENGTH_SHORT).show()
        }
    }
}

object AlbumDetailViewModel { const val ALBUM_ID_ARG = "albumId" }
object ArtistDetailViewModel { const val ARTIST_ID_ARG = "artistId" }
object PlaylistDetailViewModel { const val PLAYLIST_ID_ARG = "playlistId" }

/** Everything [MusicScreensViewModel.dailyMix] needs to decide whether today's picks are valid. */
private data class DailyInputs(
    val songs: List<Song>,
    val albums: List<Album>,
    val history: List<Song>,
    val stored: DailyPicks,
    val today: String,
    val scanning: Boolean,
)
