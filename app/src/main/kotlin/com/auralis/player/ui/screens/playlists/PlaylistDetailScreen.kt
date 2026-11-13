package com.auralis.player.ui.screens.playlists

import com.auralis.player.ui.components.GlassToolbarButton
import com.auralis.player.ui.components.GlassTextButton
import com.auralis.player.ui.components.GlassDropdownMenu
import com.auralis.player.ui.components.GlassMenuEntry

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.player.R
import com.auralis.player.domain.model.PlaylistEntry
import com.auralis.player.ui.components.CropAspect
import com.auralis.player.ui.components.RowMenuItem
import com.auralis.player.ui.components.rememberCroppedImagePicker
import com.auralis.player.ui.screens.MenuSongRow
import com.auralis.player.ui.screens.MusicScreensViewModel
import com.auralis.player.ui.screens.PlaylistTarget
import com.auralis.player.ui.screens.PlaylistTargetDialog
import kotlinx.coroutines.flow.collectLatest

/** Index of the first track row in the page's LazyColumn: the header and the controls come before it. */
private const val FIRST_TRACK_INDEX = 2

/** Width at which the header switches from the phone layout (centred cover) to the wide layout (cover beside the name). */
private const val WIDE_BREAKPOINT_DP = 600

private val HANDLE_WIDTH = 36.dp

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit = {},
    onOpenArtist: (Long) -> Unit = {},
    bottomInset: Dp = 0.dp,
) {
    val model: MusicScreensViewModel = hiltViewModel()
    val playlist by model.playlist.collectAsState(initial = null)
    val entries by model.playlistEntries.collectAsState(initial = emptyList())
    val allSongs by model.allSongs.collectAsState(initial = emptyList())
    val playerState by model.playerState.collectAsState()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    var sort by rememberSaveable { mutableStateOf(PlaylistSort.CUSTOM) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAddSongs by rememberSaveable { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<PlaylistTarget?>(null) }
    // The order while (and just after) the user drags or moves rows, so the list never flickers back to the
    // old order while the new one is being saved; dropped as soon as the database reports the saved order.
    var localOrder by remember { mutableStateOf<List<PlaylistEntry>?>(null) }
    val latestEntries by rememberUpdatedState(entries)

    // Photos (the gallery) opens first, then the crop screen; the result becomes this playlist's cover.
    val pickCover = rememberCroppedImagePicker(
        title = "Playlist artwork",
        initialAspect = CropAspect.Square,
        maxOutputEdge = 2048,
    ) { jpeg -> model.setPlaylistArtwork(jpeg) }

    val listState = rememberLazyListState()
    val reorder = remember(listState) {
        ReorderState(
            listState = listState,
            firstTrackIndex = { FIRST_TRACK_INDEX },
            onMove = { from, to -> localOrder = (localOrder ?: latestEntries).movedItem(from, to) },
            onDragEnd = { localOrder?.let { model.reorderPlaylist(it.map(PlaylistEntry::entryId)) } },
        )
    }
    LaunchedEffect(entries) { if (!reorder.isDragging) localOrder = null }
    LaunchedEffect(reorder) {
        snapshotFlow { reorder.isDragging }.collectLatest { dragging ->
            while (dragging) {
                androidx.compose.runtime.withFrameNanos { }
                val amount = reorder.autoScrollAmount()
                if (amount != 0f) reorder.scrolledBy(listState.scrollBy(amount))
            }
        }
    }

    val ordered = localOrder ?: entries
    val canReorder = sort == PlaylistSort.CUSTOM && query.isBlank()
    val shown = remember(ordered, sort, query) { ordered.filteredBy(query).sortedFor(sort) }
    // Play / Shuffle / queue actions use the whole playlist in the order it is sorted by, not the search filter.
    val playOrder = remember(ordered, sort) { ordered.sortedFor(sort).map { it.song } }
    // Only a picture the user chose for this playlist; otherwise the accent-colour cover is drawn (never a track's artwork).
    val customCover = playlist?.takeIf { it.hasCustomArtwork }?.artworkUri
    val name = playlist?.name ?: "Playlist"
    val palette = rememberAccentPalette()
    val totalDuration = remember(entries) { formatTotalDuration(entries.sumOf { it.song.durationMs }) }
    val inPlaylist = remember(entries) { entries.mapTo(HashSet()) { it.song.id } }
    val showTitleBar by remember { derivedStateOf { listState.firstVisibleItemIndex >= 1 } }

    // The page backdrop: the same cover-tinted gradient as an album page, plus the artwork itself, softly blurred.
    val isDark = scheme.background.luminance() < 0.5f
    val stops = remember(palette, scheme.background, isDark) { backdropStops(palette, scheme.background, isDark) }
    val topColor by animateColorAsState(stops[0], tween(500), label = "playlistTop")
    val midColor by animateColorAsState(stops[1], tween(500), label = "playlistMid")
    val bottomColor by animateColorAsState(stops[2], tween(500), label = "playlistBottom")

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    /** Saves a new order produced by a menu action (move to top, up, down, bottom). */
    fun commitOrder(newOrder: List<PlaylistEntry>) {
        localOrder = newOrder
        model.reorderPlaylist(newOrder.map(PlaylistEntry::entryId))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_BREAKPOINT_DP.dp
        // Fixed behind the list: one calm, cover-derived surface, with no hard edge anywhere.
        PlaylistBackdrop(topColor, midColor, bottomColor, customCover)
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp + bottomInset),
        ) {
            item(key = "header") {
                PlaylistHeader(
                    name = name,
                    cover = { size -> CoverWithShadow(customCover, size) },
                    songCount = entries.size,
                    totalDuration = totalDuration,
                    wide = wide,
                )
            }
            item(key = "controls") {
                PlaylistControls(
                    hasTracks = entries.isNotEmpty(),
                    hasCustomArtwork = playlist?.hasCustomArtwork == true,
                    query = query,
                    onQueryChange = { query = it },
                    sort = sort,
                    onSortChange = { sort = it },
                    onPlay = { if (playOrder.isNotEmpty()) model.play(playOrder, 0) },
                    onShuffle = { if (playOrder.isNotEmpty()) model.shuffle(playOrder) },
                    onAddSongs = { showAddSongs = true },
                    onPlayNext = { model.playAllNext(playOrder); toast("Playing ${playOrder.size} songs next") },
                    onAddToQueue = { model.addAllToQueue(playOrder); toast("Added ${playOrder.size} songs to the queue") },
                    onChangeArtwork = pickCover,
                    onRemoveArtwork = { model.setPlaylistArtwork(null) },
                    onRename = { showRename = true },
                    onDelete = { showDelete = true },
                )
            }
            when {
                entries.isEmpty() -> item(key = "empty") { EmptyPlaylist(onAddSongs = { showAddSongs = true }) }
                shown.isEmpty() -> item(key = "no-match") {
                    Text(
                        "No songs in this playlist match “${query.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
                else -> itemsIndexed(shown, key = { _, entry -> entry.entryId }) { index, entry ->
                    val song = entry.song
                    val isDragged = reorder.draggedKey == entry.entryId
                    val isFirst = index == 0
                    val isLast = index == shown.lastIndex
                    // The playlist-only actions go into THE song menu (before Edit File).
                    val extraItems = buildList {
                        if (canReorder) {
                            if (!isFirst) {
                                add(RowMenuItem("Move to top", { commitOrder(shown.movedItem(index, 0)) }))
                                add(RowMenuItem("Move up", { commitOrder(shown.movedItem(index, index - 1)) }))
                            }
                            if (!isLast) {
                                add(RowMenuItem("Move down", { commitOrder(shown.movedItem(index, index + 1)) }))
                                add(RowMenuItem("Move to bottom", { commitOrder(shown.movedItem(index, shown.lastIndex)) }))
                            }
                        }
                        add(
                            RowMenuItem("Remove from this playlist", {
                                model.removePlaylistEntry(entry.entryId)
                                toast("Removed from playlist")
                            }),
                        )
                    }
                    MenuSongRow(
                        song = song,
                        model = model,
                        onPlay = { model.play(shown.map { it.song }, index) },
                        onAddToPlaylist = { pickerTarget = it },
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        modifier = (if (isDragged) Modifier.zIndex(1f) else Modifier.animateItem())
                            .graphicsLayer { if (isDragged) translationY = reorder.dragOffset }
                            .background(if (isDragged) scheme.surfaceVariant else Color.Transparent),
                        isCurrent = playerState.currentSong?.id == song.id,
                        dragHandle = if (canReorder) {
                            {
                                DragHandle(
                                    title = song.title,
                                    isFirst = isFirst,
                                    isLast = isLast,
                                    onDragStart = { reorder.start(entry.entryId) },
                                    onDrag = { reorder.drag(it) },
                                    onDragEnd = { reorder.end() },
                                    onMoveUp = { commitOrder(shown.movedItem(index, index - 1)) },
                                    onMoveDown = { commitOrder(shown.movedItem(index, index + 1)) },
                                )
                            }
                        } else {
                            null
                        },
                        extraItems = extraItems,
                    )
                }
            }
        }

        // Back button that stays put while the list scrolls; once the header has scrolled away it becomes a title bar.
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (showTitleBar) Modifier.background(topColor) else Modifier)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassToolbarButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .then(if (showTitleBar) Modifier else Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape)),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (showTitleBar) scheme.onSurface else Color.White,
                )
            }
            if (showTitleBar) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = showAddSongs,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut() + slideOutVertically { it / 6 },
        ) {
            AddSongsPanel(
                playlistName = name,
                songs = allSongs,
                playlistSongIds = inPlaylist,
                onAdd = { song -> model.addSongToPlaylist(song.id) },
                onClose = { showAddSongs = false },
            )
        }
    }

    PlaylistTargetDialog(pickerTarget, model) { pickerTarget = null }
    if (showRename) {
        PlaylistNameDialog("Rename playlist", "Save", initial = name, onDismiss = { showRename = false }) { newName ->
            model.renamePlaylist(newName)
            showRename = false
        }
    }
    val current = playlist
    if (showDelete && current != null) {
        DeletePlaylistDialog(
            playlist = current,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                model.deletePlaylist()
                onBack()
            },
        )
    }
}

/** The drag handle that opens a playlist row; the accessibility actions offer the same move as a drag. */
@Composable
private fun DragHandle(
    title: String,
    isFirst: Boolean,
    isLast: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Box(
        Modifier
            .width(HANDLE_WIDTH)
            .height(48.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, amount -> change.consume(); onDrag(amount.y) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            }
            .semantics {
                contentDescription = "Reorder $title"
                customActions = buildList {
                    if (!isFirst) add(CustomAccessibilityAction("Move up") { onMoveUp(); true })
                    if (!isLast) add(CustomAccessibilityAction("Move down") { onMoveDown(); true })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Cover, name and facts, laid straight onto the page backdrop (the backdrop supplies the colour). Shared by
 * playlist pages and the pages of the automatic playlists: [cover] draws the artwork at the size it is given and
 * [kindLabel] is the word before the facts ("Playlist", "Collection").
 */
@Composable
internal fun PlaylistHeader(
    name: String,
    cover: @Composable (sizeDp: Int) -> Unit,
    songCount: Int,
    totalDuration: String,
    wide: Boolean,
    kindLabel: String = "Playlist",
) {
    val scheme = MaterialTheme.colorScheme
    val meta = listOf(kindLabel, stringResource(R.string.playlists_song_count, songCount), totalDuration)
        .filter { it.isNotBlank() }.joinToString(" · ")
    if (wide) {
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 64.dp, bottom = 20.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            cover(200)
            Column(Modifier.weight(1f)) {
                Text(meta, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
    } else {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            cover(210)
            Spacer(Modifier.height(18.dp))
            Text(
                name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun CoverWithShadow(customArtwork: String?, sizeDp: Int) {
    PlaylistCover(
        customArtwork = customArtwork,
        size = sizeDp.dp,
        modifier = Modifier.shadow(18.dp, RoundedCornerShape(12.dp)),
    )
}

/**
 * The row of actions under the header (add songs, more, shuffle, the big play button) and the
 * find-in-playlist field with the sort menu. The "more" menu also holds the artwork actions.
 */
@Composable
private fun PlaylistControls(
    hasTracks: Boolean,
    hasCustomArtwork: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: PlaylistSort,
    onSortChange: (PlaylistSort) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddSongs: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onChangeArtwork: () -> Unit,
    onRemoveArtwork: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var moreOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassToolbarButton(onClick = onAddSongs) { Icon(Icons.Default.Add, contentDescription = "Add songs") }
            Box {
                GlassToolbarButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Playlist actions") }
                GlassDropdownMenu(
                    expanded = moreOpen,
                    onDismissRequest = { moreOpen = false },
                    entries = buildList {
                        if (hasTracks) {
                            add(GlassMenuEntry("Play Next", onClick = onPlayNext))
                            add(GlassMenuEntry("Add to Queue", onClick = onAddToQueue))
                        }
                        add(GlassMenuEntry("Add songs", onClick = onAddSongs))
                        add(GlassMenuEntry(if (hasCustomArtwork) "Change artwork" else "Choose artwork", onClick = onChangeArtwork))
                        if (hasCustomArtwork) add(GlassMenuEntry("Remove artwork", onClick = onRemoveArtwork))
                        add(GlassMenuEntry("Rename", onClick = onRename))
                        add(GlassMenuEntry("Delete playlist", onClick = onDelete, destructive = true, dividerBefore = true))
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            GlassToolbarButton(onClick = onShuffle, enabled = hasTracks) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle play") }
            Spacer(Modifier.width(4.dp))
            GlassToolbarButton(
                onClick = onPlay,
                enabled = hasTracks,
                modifier = Modifier
                    .size(56.dp)
                    .background(if (hasTracks) scheme.primary else scheme.onSurface.copy(alpha = 0.12f), CircleShape),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = if (hasTracks) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        if (hasTracks) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlaylistFindField(query, onQueryChange, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Box {
                    GlassTextButton(onClick = { sortOpen = true }) {
                        Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(sort.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    GlassDropdownMenu(
                        expanded = sortOpen,
                        onDismissRequest = { sortOpen = false },
                        entries = PlaylistSort.entries.map { option ->
                            GlassMenuEntry(option.label, onClick = { onSortChange(option) }, selected = option == sort, autoIcon = false)
                        },
                    )
                }
            }
        }
    }
}

/** The "Find in playlist" field under the playlist controls; also used by the automatic playlists' pages. */
@Composable
internal fun PlaylistFindField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Find in playlist",
) {
    val ink = MaterialTheme.colorScheme.onSurface
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.heightIn(min = 48.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                GlassToolbarButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, contentDescription = "Clear search") }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
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

@Composable
private fun EmptyPlaylist(onAddSongs: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Let's find something for your playlist", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Add songs from your library and arrange them the way you like.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button, onClick = onAddSongs)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text("Add songs", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
