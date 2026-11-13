package com.auralis.player.ui.screens.playlists

import com.auralis.player.ui.components.GlassToolbarButton

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.player.R
import com.auralis.player.domain.model.Playlist
import com.auralis.player.domain.model.Song
import com.auralis.player.ui.components.RowMenuButton
import com.auralis.player.ui.components.RowMenuItem
import com.auralis.player.ui.components.ScreenHeader
import com.auralis.player.ui.navigation.Destination
import com.auralis.player.ui.navigation.ScrollResetEffect
import com.auralis.player.ui.screens.MusicScreensViewModel

/** The grid is used for both layouts: one column for the list, adaptive columns for the tiles. */
private val TILE_MIN_WIDTH = 150.dp

@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenCollection: (SmartCollection) -> Unit,
    bottomInset: Dp = 0.dp,
) {
    val model: MusicScreensViewModel = hiltViewModel()
    val context = LocalContext.current
    val playlists by model.playlists.collectAsState(initial = emptyList())
    // The three automatic playlists read the library directly (every track, no copies) and re-emit on every like or play.
    val liked by model.favoriteSongsAll.collectAsState(initial = emptyList())
    val recentlyPlayed by model.recentlyPlayedAll.collectAsState(initial = emptyList())
    val mostPlayed by model.mostPlayedAll.collectAsState(initial = emptyList())
    // Stored in DataStore, so the choice stays while moving around the app and across restarts.
    val sortMode by model.playlistLibrarySort.collectAsState(initial = PlaylistLibrarySort.RECENTS)

    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var gridView by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val gridState = rememberLazyGridState()
    ScrollResetEffect(Destination.Playlists.route) { gridState.scrollToItem(0) }

    val needle = query.trim()
    val visible = remember(playlists, sortMode, needle) { playlists.forLibrary(sortMode, needle) }
    fun matches(title: String) = needle.isEmpty() || title.contains(needle, ignoreCase = true)

    val likedTitle = stringResource(SmartCollection.FAVORITES.titleRes)
    val recentTitle = stringResource(SmartCollection.RECENTLY_PLAYED.titleRes)
    val mostTitle = stringResource(SmartCollection.MOST_PLAYED.titleRes)
    val collections = buildList {
        if (matches(likedTitle)) add(LibraryCollection(SmartCollection.FAVORITES, likedTitle, liked))
        if (matches(recentTitle)) add(LibraryCollection(SmartCollection.RECENTLY_PLAYED, recentTitle, recentlyPlayed))
        if (matches(mostTitle)) add(LibraryCollection(SmartCollection.MOST_PLAYED, mostTitle, mostPlayed))
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    fun closeSearch() {
        searching = false
        query = ""
    }

    // The NavHost draws this screen edge-to-edge; keep the controls below the status bar while
    // allowing the same app backdrop to flow behind the full screen and floating bottom panels.
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = "Playlists",
            action = {
                GlassToolbarButton(onClick = { if (searching) closeSearch() else searching = true }) {
                    Icon(
                        if (searching) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (searching) "Close search" else "Search playlists",
                    )
                }
                GlassToolbarButton(onClick = { gridView = !gridView }) {
                    Icon(
                        if (gridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = if (gridView) "Show as list" else "Show as grid",
                    )
                }
                GlassToolbarButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create playlist")
                }
            },
        )
        AnimatedVisibility(
            visible = searching,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            LibrarySearchField(query = query, onQueryChange = { query = it }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        LazyVerticalGrid(
            columns = if (gridView) GridCells.Adaptive(TILE_MIN_WIDTH) else GridCells.Fixed(1),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = if (gridView) 8.dp else 0.dp,
                end = if (gridView) 8.dp else 0.dp,
                bottom = 24.dp + bottomInset,
            ),
        ) {
            items(collections, key = { it.collection.key }) { collection ->
                val songs = collection.songs
                val menu = buildList {
                    if (songs.isNotEmpty()) {
                        add(RowMenuItem("Play", { model.play(songs) }))
                        add(RowMenuItem("Shuffle", { model.shuffle(songs) }))
                        add(RowMenuItem("Play Next", { model.playAllNext(songs); toast("Playing ${songs.size} songs next") }))
                        add(RowMenuItem("Add to Queue", { model.addAllToQueue(songs); toast("Added ${songs.size} songs to the queue") }))
                    }
                }
                LibraryEntry(
                    cover = { size -> CollectionCover(collection.collection.icon, size) },
                    title = collection.title,
                    subtitle = "Collection · " + stringResource(R.string.playlists_song_count, songs.size),
                    grid = gridView,
                    clickLabel = "Open ${collection.title}",
                    onClick = { onOpenCollection(collection.collection) },
                    menu = menu,
                    menuLabel = "${collection.title} actions",
                )
            }
            if (playlists.isEmpty() && needle.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyLibrary(onCreate = { showCreate = true })
                }
            } else if (visible.isEmpty()) {
                item(key = "no-match", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No playlists match “$needle”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            } else {
                // The sort control sits directly above the heading of the user's own playlists.
                item(key = "sort", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(start = if (gridView) 8.dp else 16.dp, end = 16.dp, top = 12.dp)) {
                        PlaylistSortControl(selected = sortMode, onSelect = model::setPlaylistLibrarySort)
                    }
                }
                item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Your playlists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = if (gridView) 8.dp else 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                    )
                }
                items(visible, key = { it.id }) { playlist ->
                    val hasSongs = playlist.songCount > 0
                    val menu = buildList {
                        if (hasSongs) {
                            add(RowMenuItem("Play", { model.playPlaylist(playlist.id, shuffled = false) }))
                            add(RowMenuItem("Shuffle", { model.playPlaylist(playlist.id, shuffled = true) }))
                            add(RowMenuItem("Play Next", { model.playPlaylistNext(playlist.id); toast("Playing next") }))
                            add(RowMenuItem("Add to Queue", { model.addPlaylistToQueue(playlist.id); toast("Added to the queue") }))
                        }
                        add(RowMenuItem("Rename", { renameTarget = playlist }))
                        add(RowMenuItem("Delete", { deleteTarget = playlist }))
                    }
                    LibraryEntry(
                        cover = { size -> PlaylistCover(playlist.artworkUri, size) },
                        title = playlist.name,
                        subtitle = "Playlist · " + stringResource(R.string.playlists_song_count, playlist.songCount),
                        grid = gridView,
                        clickLabel = "Open ${playlist.name}",
                        onClick = { onOpenPlaylist(playlist.id) },
                        menu = menu,
                        menuLabel = "${playlist.name} actions",
                    )
                }
            }
        }
    }

    if (showCreate) {
        PlaylistNameDialog("New playlist", "Create", onDismiss = { showCreate = false }) { name ->
            showCreate = false
            // Like Spotify, a new playlist opens straight away so songs can be added to it.
            model.createPlaylistAndOpen(name) { id -> onOpenPlaylist(id) }
        }
    }
    renameTarget?.let { target ->
        PlaylistNameDialog("Rename playlist", "Save", initial = target.name, onDismiss = { renameTarget = null }) { name ->
            model.renamePlaylist(target.id, name)
            renameTarget = null
        }
    }
    deleteTarget?.let { target ->
        DeletePlaylistDialog(
            playlist = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                model.deletePlaylist(target.id)
                deleteTarget = null
            },
        )
    }
}

/** A built-in collection shown above the user's playlists. [songs] is what Play, Shuffle and the queue actions use. */
private class LibraryCollection(val collection: SmartCollection, val title: String, val songs: List<Song>)

private val SORT_SEGMENT_WIDTH = 84.dp

/**
 * The two-way sort switch above "Your playlists" (Recents / A–Z), styled like a native segmented control: one soft
 * track with a pill that slides to the chosen option. Choosing an option re-sorts the list at once.
 */
@Composable
private fun PlaylistSortControl(selected: PlaylistLibrarySort, onSelect: (PlaylistLibrarySort) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val options = PlaylistLibrarySort.entries
    val pillOffset by animateDpAsState(SORT_SEGMENT_WIDTH * options.indexOf(selected), tween(220), label = "sortPill")
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.onSurface.copy(alpha = 0.08f))
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = pillOffset)
                .width(SORT_SEGMENT_WIDTH)
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.onSurface.copy(alpha = 0.16f)),
        )
        Row {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    Modifier
                        .width(SORT_SEGMENT_WIDTH)
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * One library entry, as a list row or a grid tile: the [cover] (drawn at the size it is given), a title and
 * a subtitle, and the three-dot [menu] when it has entries. Tapping it runs [onClick].
 */
@Composable
private fun LibraryEntry(
    cover: @Composable (Dp) -> Unit,
    title: String,
    subtitle: String,
    grid: Boolean,
    clickLabel: String,
    onClick: () -> Unit,
    menu: List<RowMenuItem>,
    menuLabel: String,
) {
    val scheme = MaterialTheme.colorScheme
    if (grid) {
        Column(
            Modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
                .padding(8.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) { cover(maxWidth) }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (menu.isNotEmpty()) RowMenuButton(items = menu, contentDescription = menuLabel)
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
                .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cover(56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (menu.isNotEmpty()) RowMenuButton(items = menu, contentDescription = menuLabel)
        }
    }
}

@Composable
private fun EmptyLibrary(onCreate: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Create your first playlist", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Collect the songs you love and arrange them your way.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(scheme.primary)
                .clickable(role = Role.Button, onClick = onCreate)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text("Create playlist", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = scheme.onPrimary)
        }
    }
}

@Composable
private fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    val ink = MaterialTheme.colorScheme.onSurface
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        placeholder = { Text("Search playlists") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                GlassToolbarButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, contentDescription = "Clear search") }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ink.copy(alpha = 0.08f),
            unfocusedContainerColor = ink.copy(alpha = 0.08f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
    )
}
