package com.auralis.player.ui.screens.search

import com.auralis.player.ui.components.GlassTextButton
import com.auralis.player.ui.components.GlassSegmentedTabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.auralis.player.ui.navigation.Destination
import com.auralis.player.ui.navigation.ScrollResetEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.auralis.player.R
import com.auralis.player.ui.components.AlbumGridTile
import com.auralis.player.ui.components.ArtistRow
import com.auralis.player.ui.components.EmptyState
import com.auralis.player.ui.components.ScreenHeader
import com.auralis.player.ui.screens.AlbumCard
import com.auralis.player.ui.screens.MenuSongRow
import com.auralis.player.ui.screens.MusicScreensViewModel
import com.auralis.player.ui.screens.PlaylistTarget
import com.auralis.player.ui.screens.PlaylistTargetDialog

@Composable
fun SearchScreen(onOpenAlbum: (Long) -> Unit, onOpenArtist: (Long) -> Unit) {
    val model: MusicScreensViewModel = hiltViewModel()
    val songs by model.searchSongs.collectAsState(initial = emptyList())
    val albums by model.searchAlbums.collectAsState(initial = emptyList())
    val artists by model.searchArtists.collectAsState(initial = emptyList())
    val searchHistory by model.searchHistory.collectAsState(initial = emptyList())
    // Album cards show the album artist, like the Library, so Search needs every artist's name.
    val allArtists by model.artists.collectAsState(initial = emptyList())
    val artistNames = remember(allArtists) { allArtists.associate { it.id to it.name } }
    val unknownArtist = stringResource(R.string.library_unknown_artist)
    // What the "Add to Playlist" picker is adding: one song, or every track of an album.
    var playlistTarget by remember { mutableStateOf<PlaylistTarget?>(null) }
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Songs", "Albums", "Artists")
    val songsState = rememberLazyListState()
    val albumsState = rememberLazyGridState()
    val artistsState = rememberLazyListState()
    ScrollResetEffect(Destination.Search.route) {
        songsState.scrollToItem(0)
        albumsState.scrollToItem(0)
        artistsState.scrollToItem(0)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Search")
        SearchBar(
            query = query,
            onQueryChange = { query = it; model.search(it) },
            onSearch = {
                model.rememberSearch(query)
                focusManager.clearFocus()
                keyboard?.hide()
            },
            onCancel = {
                // Clears the query (and with it every result filter), so the screen returns to its
                // default state, and puts the keyboard away.
                query = ""
                model.search("")
                focusManager.clearFocus()
                keyboard?.hide()
            },
        )
        GlassSegmentedTabs(labels = tabs, selectedIndex = selectedTab, onSelect = { selectedTab = it })
        if (query.isBlank()) {
            if (searchHistory.isEmpty()) {
                EmptyState("Search your music", "Type a song, album, or artist name.")
            } else {
                // Plain, spacious list: search icon + query per entry, newest first, no cards or dividers.
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "history_header") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.search_history), style = MaterialTheme.typography.titleMedium)
                            GlassTextButton(onClick = model::clearSearchHistory) { Text(stringResource(R.string.clear)) }
                        }
                    }
                    items(searchHistory, key = { it }) { recent ->
                        RecentSearchRow(
                            text = recent,
                            onClick = {
                                // Runs the search again; remembering it moves it back to the top of the history.
                                query = recent
                                model.search(recent)
                                model.rememberSearch(recent)
                                focusManager.clearFocus()
                                keyboard?.hide()
                            },
                        )
                    }
                }
            }
        } else {
            when (selectedTab) {
                0 -> if (songs.isEmpty()) EmptyState("No matching songs", "Try a different search.") else {
                    LazyColumn(Modifier.fillMaxSize(), state = songsState) {
                        items(songs, key = { it.id }) { song ->
                            // The same row as Library > Songs: heart + three-dot menu only.
                            MenuSongRow(
                                song,
                                model,
                                onPlay = { model.play(songs, songs.indexOf(song)) },
                                onAddToPlaylist = { playlistTarget = it },
                                onOpenArtist = onOpenArtist,
                                onOpenAlbum = onOpenAlbum,
                            )
                        }
                    }
                }
                1 -> if (albums.isEmpty()) EmptyState("No matching albums", "Try a different search.") else {
                    // Same cards and grid as the Library's Albums tab, so covers are the normal size.
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        state = albumsState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(albums, key = { it.id }) { album ->
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
                else -> if (artists.isEmpty()) EmptyState("No matching artists", "Try a different search.") else {
                    LazyColumn(Modifier.fillMaxSize(), state = artistsState) {
                        items(artists, key = { it.id }) { artist ->
                            ArtistRow(artist, onClick = { onOpenArtist(artist.id) })
                        }
                    }
                }
            }
        }
    }
    PlaylistTargetDialog(playlistTarget, model) { playlistTarget = null }
}

/**
 * One entry of "Recent searches": a magnifying glass at a fixed left edge and the query starting at
 * a fixed position beside it, vertically centred on each other. Long queries wrap onto further lines
 * inside the text column, so the icon column and the text start never move. Proportions follow the
 * reference design: a single line is about 54 dp tall (also the touch target) and wrapped entries
 * grow by one 26 sp text line each.
 */
@Composable
private fun RecentSearchRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}

private val SearchFieldHeight = 38.dp
private val SearchFieldShape = RoundedCornerShape(12.dp)

/**
 * Minimal search row: a light-gray rounded field (search icon on the left, placeholder, the query)
 * that fills the width. The pink/red "Cancel" action only exists while there is a query: it fades and
 * slides in from the end as the user starts typing and back out when the field is empty again. The
 * field's width follows that animation smoothly and the row keeps a constant height, so nothing below
 * it jumps. Colours come from the app theme; only the Cancel red is fixed, in a slightly deeper shade
 * on light themes so it keeps enough contrast.
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val fieldColor = colors.onSurface.copy(alpha = 0.08f)
    val hintColor = colors.onSurfaceVariant
    val cancelColor = if (isDark) Color(0xFFFA2D48) else Color(0xFFD81B3B)
    val placeholder = stringResource(R.string.search_field_placeholder)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)

    Row(
        // Fixed height = the Cancel button's 48 dp touch target plus padding, with or without Cancel.
        modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .height(SearchFieldHeight)
                .semantics { contentDescription = placeholder },
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Row(
                    Modifier.fillMaxSize().clip(SearchFieldShape).background(fieldColor).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = hintColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(placeholder, style = textStyle, color = hintColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        innerTextField()
                    }
                }
            },
        )
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(tween(180)) +
                slideInHorizontally(tween(220)) { it / 2 } +
                expandHorizontally(tween(220), expandFrom = Alignment.End),
            exit = fadeOut(tween(140)) +
                slideOutHorizontally(tween(200)) { it / 2 } +
                shrinkHorizontally(tween(200), shrinkTowards = Alignment.End),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.search_cancel),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cancelColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onCancel)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}
