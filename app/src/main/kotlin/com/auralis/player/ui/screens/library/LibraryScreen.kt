package com.auralis.player.ui.screens.library

import com.auralis.player.ui.components.GlassDropdownMenu
import com.auralis.player.ui.components.GlassMenuEntry
import com.auralis.player.ui.components.GlassChip
import com.auralis.player.ui.components.GlassSegmentedTabs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.player.R
import com.auralis.player.settings.AlbumSort
import com.auralis.player.settings.AppSettings
import com.auralis.player.settings.ArtistSort
import com.auralis.player.settings.SongSort
import com.auralis.player.ui.components.AlbumGridTile
import com.auralis.player.ui.components.ArtistRow
import com.auralis.player.ui.components.EmptyState
import com.auralis.player.ui.components.ScreenHeader
import com.auralis.player.ui.navigation.Destination
import com.auralis.player.ui.navigation.ScrollResetEffect
import com.auralis.player.ui.screens.AlbumCard
import com.auralis.player.ui.screens.MenuSongRow
import com.auralis.player.ui.screens.MusicScreensViewModel
import com.auralis.player.ui.screens.PlaylistTarget
import com.auralis.player.ui.screens.PlaylistTargetDialog
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(onOpenAlbum: (Long) -> Unit, onOpenArtist: (Long) -> Unit) {
    val model: MusicScreensViewModel = hiltViewModel()
    val songs by model.allSongs.collectAsState(initial = emptyList())
    val albums by model.albums.collectAsState(initial = emptyList())
    val artists by model.artists.collectAsState(initial = emptyList())
    val settings by model.appSettings.collectAsState(initial = AppSettings())
    val artistNames = remember(artists) { artists.associate { it.id to it.name } }
    val unknownArtist = stringResource(R.string.library_unknown_artist)
    val scope = rememberCoroutineScope()
    // What the "Add to Playlist" picker is adding: one song, or every track of an album.
    var playlistTarget by remember { mutableStateOf<PlaylistTarget?>(null) }

    // Each tab keeps its own sort (persisted, shared with Settings) and its own genre filter.
    var songGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var albumGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var artistGenre by rememberSaveable { mutableStateOf<String?>(null) }

    val songsState: LazyListState = rememberLazyListState()
    val albumsState: LazyGridState = rememberLazyGridState()
    val artistsState: LazyListState = rememberLazyListState()
    ScrollResetEffect(Destination.Library.route) {
        songsState.scrollToItem(0)
        albumsState.scrollToItem(0)
        artistsState.scrollToItem(0)
    }

    // Genres come from the songs' real tags; albums and artists inherit the genres of their songs.
    val genreNames = remember(songs) { genreDisplayNames(songs) }
    val albumGenres = remember(songs) { albumGenreMap(songs) }
    val artistGenres = remember(songs, albums) { artistGenreMap(songs, albums) }
    val songChoices = remember(songs, genreNames) { genreChoices(songs.map(::songGenreSet), genreNames) }
    val albumChoices = remember(albums, albumGenres, genreNames) {
        genreChoices(albums.map { albumGenres[it.id].orEmpty() }, genreNames)
    }
    val artistChoices = remember(artists, artistGenres, genreNames) {
        genreChoices(artists.map { artistGenres[it.id].orEmpty() }, genreNames)
    }
    // A saved filter that no longer exists (library changed) quietly falls back to All Genres.
    val activeSongGenre = songGenre?.takeIf(songChoices::accepts)
    val activeAlbumGenre = albumGenre?.takeIf(albumChoices::accepts)
    val activeArtistGenre = artistGenre?.takeIf(artistChoices::accepts)

    val visibleSongs = remember(songs, albums, settings.songSort, activeSongGenre) {
        sortSongs(filterSongsByGenre(songs, activeSongGenre), settings.songSort, albums)
    }
    val visibleAlbums = remember(albums, albumGenres, artistNames, settings.albumSort, activeAlbumGenre) {
        sortAlbums(filterAlbumsByGenre(albums, albumGenres, activeAlbumGenre), settings.albumSort) { album ->
            album.albumArtistId?.let(artistNames::get)
        }
    }
    val visibleArtists = remember(artists, albums, artistGenres, settings.artistSort, activeArtistGenre) {
        sortArtists(filterArtistsByGenre(artists, artistGenres, activeArtistGenre), settings.artistSort, albums)
    }

    // A-Z side index per tab: built from the list exactly as displayed (after sorting and the genre
    // filter), and only while the sort is alphabetical by the field the letters come from.
    val songIndex = remember(visibleSongs, settings.songSort) {
        songIndexText(settings.songSort)?.let { textOf -> buildAlphabetIndex(visibleSongs, textOf) }
    }
    val albumIndex = remember(visibleAlbums, artistNames, settings.albumSort) {
        albumIndexText(settings.albumSort) { album -> album.albumArtistId?.let(artistNames::get) }
            ?.let { textOf -> buildAlphabetIndex(visibleAlbums, textOf) }
    }
    val artistIndex = remember(visibleArtists, settings.artistSort) {
        artistIndexText(settings.artistSort)?.let { textOf -> buildAlphabetIndex(visibleArtists, textOf) }
    }

    // Saved (not just remembered) so leaving the Library and coming back, via the bottom bar or the
    // back button from an album/artist page, returns to the subsection that was open instead of Songs.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.library_tab_songs),
        stringResource(R.string.library_tab_albums),
        stringResource(R.string.library_tab_artists),
    )

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_library))
        GlassSegmentedTabs(labels = tabs, selectedIndex = selectedTab, onSelect = { selectedTab = it })
        when (selectedTab) {
            0 -> if (songs.isEmpty()) {
                EmptyState("Nothing here yet", "Add a music folder from Settings.")
            } else {
                val sortEntries = listOf(
                    MenuEntry(SongSort.TITLE, stringResource(R.string.lib_sort_az)),
                    MenuEntry(SongSort.ARTIST, stringResource(R.string.lib_sort_artist)),
                    MenuEntry(SongSort.YEAR, stringResource(R.string.lib_sort_release_year)),
                    MenuEntry(SongSort.ALBUM, stringResource(R.string.sort_album)),
                    MenuEntry(SongSort.DATE_ADDED, stringResource(R.string.sort_date_added)),
                    MenuEntry(SongSort.DURATION, stringResource(R.string.sort_duration)),
                )
                LibraryControls(
                    sortEntries = sortEntries,
                    sortSelected = settings.songSort,
                    onSortSelect = { model.setSongSort(it); scope.launch { songsState.scrollToItem(0) } },
                    genreChoices = songChoices,
                    genreSelected = activeSongGenre,
                    onGenreSelect = { songGenre = it; scope.launch { songsState.scrollToItem(0) } },
                )
                if (visibleSongs.isEmpty()) {
                    NoMatches()
                } else {
                    IndexedListBox(
                        index = songIndex,
                        firstVisibleItem = { songsState.firstVisibleItemIndex },
                        onJump = { position, animate ->
                            scope.launch {
                                if (animate) songsState.animateScrollToItem(position) else songsState.scrollToItem(position)
                            }
                        },
                    ) { endPadding ->
                        LazyColumn(Modifier.fillMaxSize(), state = songsState, contentPadding = PaddingValues(end = endPadding)) {
                            items(visibleSongs, key = { it.id }) { song ->
                                MenuSongRow(
                                    song,
                                    model,
                                    onPlay = { model.play(visibleSongs, visibleSongs.indexOf(song)) },
                                    onAddToPlaylist = { playlistTarget = it },
                                    onOpenArtist = onOpenArtist,
                                    onOpenAlbum = onOpenAlbum,
                                )
                            }
                        }
                    }
                }
            }
            1 -> if (albums.isEmpty()) {
                EmptyState("No albums yet", "Albums appear here after your music is scanned.")
            } else {
                val sortEntries = listOf(
                    MenuEntry(AlbumSort.TITLE, stringResource(R.string.lib_sort_az)),
                    MenuEntry(AlbumSort.ARTIST, stringResource(R.string.lib_sort_artist)),
                    MenuEntry(AlbumSort.YEAR, stringResource(R.string.lib_sort_release_year)),
                    MenuEntry(AlbumSort.RECENT, stringResource(R.string.lib_sort_recent)),
                    MenuEntry(AlbumSort.FAVORITES, stringResource(R.string.lib_sort_favorites)),
                )
                LibraryControls(
                    sortEntries = sortEntries,
                    sortSelected = settings.albumSort,
                    onSortSelect = { model.setAlbumSort(it); scope.launch { albumsState.scrollToItem(0) } },
                    genreChoices = albumChoices,
                    genreSelected = activeAlbumGenre,
                    onGenreSelect = { albumGenre = it; scope.launch { albumsState.scrollToItem(0) } },
                )
                if (visibleAlbums.isEmpty()) {
                    NoMatches()
                } else {
                    IndexedListBox(
                        index = albumIndex,
                        firstVisibleItem = { albumsState.firstVisibleItemIndex },
                        onJump = { position, animate ->
                            scope.launch {
                                if (animate) albumsState.animateScrollToItem(position) else albumsState.scrollToItem(position)
                            }
                        },
                    ) { endPadding ->
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            state = albumsState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp + endPadding, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(visibleAlbums, key = { it.id }) { album ->
                                AlbumCard(
                                    album,
                                    artistName = album.albumArtistId?.let(artistNames::get) ?: unknownArtist,
                                    model = model,
                                    onOpen = { onOpenAlbum(album.id) },
                                    onAddToPlaylist = { playlistTarget = it },
                                    onOpenArtist = onOpenArtist,
                                )
                            }
                        }
                    }
                }
            }
            else -> if (artists.isEmpty()) {
                EmptyState("No artists yet", "Artists appear here after your music is scanned.")
            } else {
                // For artists "Artist" and "A-Z" would be the same order, so there is a single entry.
                val sortEntries = listOf(
                    MenuEntry(ArtistSort.NAME, stringResource(R.string.lib_sort_artist_az)),
                    MenuEntry(ArtistSort.YEAR, stringResource(R.string.lib_sort_release_year)),
                    MenuEntry(ArtistSort.FAVORITES, stringResource(R.string.lib_sort_favorites)),
                )
                LibraryControls(
                    sortEntries = sortEntries,
                    sortSelected = settings.artistSort,
                    onSortSelect = { model.setArtistSort(it); scope.launch { artistsState.scrollToItem(0) } },
                    genreChoices = artistChoices,
                    genreSelected = activeArtistGenre,
                    onGenreSelect = { artistGenre = it; scope.launch { artistsState.scrollToItem(0) } },
                )
                if (visibleArtists.isEmpty()) {
                    NoMatches()
                } else {
                    IndexedListBox(
                        index = artistIndex,
                        firstVisibleItem = { artistsState.firstVisibleItemIndex },
                        onJump = { position, animate ->
                            scope.launch {
                                if (animate) artistsState.animateScrollToItem(position) else artistsState.scrollToItem(position)
                            }
                        },
                    ) { endPadding ->
                        LazyColumn(Modifier.fillMaxSize(), state = artistsState, contentPadding = PaddingValues(end = endPadding)) {
                            items(visibleArtists, key = { it.id }) { artist ->
                                ArtistRow(artist, onClick = { onOpenArtist(artist.id) }) {
                                    model.favoriteArtist(artist.id, artist.isFavorite)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    PlaylistTargetDialog(playlistTarget, model) { playlistTarget = null }
}

@Composable
private fun NoMatches() {
    EmptyState(stringResource(R.string.lib_no_matches_title), stringResource(R.string.lib_no_matches_body))
}

private data class MenuEntry<T>(val value: T, val label: String)

/** The two controls above a Library list: Sort By and Genres. Scrolls sideways on narrow screens. */
@Composable
private fun <S> LibraryControls(
    sortEntries: List<MenuEntry<S>>,
    sortSelected: S,
    onSortSelect: (S) -> Unit,
    genreChoices: GenreChoices,
    genreSelected: String?,
    onGenreSelect: (String?) -> Unit,
) {
    val sortLabel = sortEntries.firstOrNull { it.value == sortSelected }?.label.orEmpty()
    val allGenres = stringResource(R.string.lib_genre_all)
    val unknownGenre = stringResource(R.string.lib_genre_unknown)
    val genreEntries = buildList {
        add(MenuEntry<String?>(null, allGenres))
        genreChoices.names.forEach { add(MenuEntry<String?>(it, it)) }
        if (genreChoices.hasUnknown) add(MenuEntry<String?>(UNKNOWN_GENRE, unknownGenre))
    }
    val genreLabel = when (genreSelected) {
        null -> allGenres
        UNKNOWN_GENRE -> unknownGenre
        else -> genreSelected
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DropdownChip(
            label = stringResource(R.string.lib_sort_chip, sortLabel),
            highlighted = false,
            entries = sortEntries,
            isSelected = { it == sortSelected },
            onSelect = onSortSelect,
        )
        DropdownChip(
            label = genreLabel,
            highlighted = genreSelected != null,
            entries = genreEntries,
            isSelected = { it == genreSelected },
            onSelect = onGenreSelect,
        )
    }
}

@Composable
private fun <T> DropdownChip(
    label: String,
    highlighted: Boolean,
    entries: List<MenuEntry<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlassChip(
            label = label,
            onClick = { open = true },
            selected = highlighted,
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
        GlassDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            entries = entries.map { entry ->
                GlassMenuEntry(entry.label, onClick = { onSelect(entry.value) }, selected = isSelected(entry.value), autoIcon = false)
            },
        )
    }
}
