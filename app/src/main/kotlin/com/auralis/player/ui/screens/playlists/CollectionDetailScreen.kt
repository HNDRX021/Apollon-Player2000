package com.auralis.player.ui.screens.playlists

import com.auralis.player.ui.components.GlassToolbarButton
import com.auralis.player.ui.components.GlassDropdownMenu
import com.auralis.player.ui.components.GlassMenuEntry

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.player.domain.model.Song
import com.auralis.player.ui.components.RowMenuItem
import com.auralis.player.ui.screens.MenuSongRow
import com.auralis.player.ui.screens.MusicScreensViewModel
import com.auralis.player.ui.screens.PlaylistTarget
import com.auralis.player.ui.screens.PlaylistTargetDialog

private const val COLLECTION_WIDE_BREAKPOINT_DP = 600

/**
 * The page of Favorites, Recently Played or Most Played. It is laid out exactly like a playlist page (cover-tinted
 * backdrop, header, Play / Shuffle controls, the shared song rows with the inline heart and three-dot menu) but
 * its songs are never stored: they come live from the library, already ordered (liked songs newest-liked first,
 * history newest first, play count highest first), and the list updates as soon as a like or a play is recorded.
 */
@Composable
fun CollectionDetailScreen(
    collection: SmartCollection,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit = {},
    onOpenArtist: (Long) -> Unit = {},
    bottomInset: Dp = 0.dp,
) {
    val model: MusicScreensViewModel = hiltViewModel()
    val songsFlow = remember(model, collection) {
        when (collection) {
            SmartCollection.FAVORITES -> model.favoriteSongsAll
            SmartCollection.RECENTLY_PLAYED -> model.recentlyPlayedAll
            SmartCollection.MOST_PLAYED -> model.mostPlayedAll
        }
    }
    val songs by songsFlow.collectAsState(initial = emptyList())
    val playerState by model.playerState.collectAsState()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    var query by rememberSaveable { mutableStateOf("") }
    var pickerTarget by remember { mutableStateOf<PlaylistTarget?>(null) }

    val name = stringResource(collection.titleRes)
    val shown = remember(songs, query) { songs.matching(query) }
    val totalDuration = remember(songs) { formatTotalDuration(songs.sumOf { it.durationMs }) }

    val palette = rememberAccentPalette()
    val isDark = scheme.background.luminance() < 0.5f
    val stops = remember(palette, scheme.background, isDark) { backdropStops(palette, scheme.background, isDark) }
    val topColor by animateColorAsState(stops[0], tween(500), label = "collectionTop")
    val midColor by animateColorAsState(stops[1], tween(500), label = "collectionMid")
    val bottomColor by animateColorAsState(stops[2], tween(500), label = "collectionBottom")

    val listState = rememberLazyListState()
    val showTitleBar by remember { derivedStateOf { listState.firstVisibleItemIndex >= 1 } }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= COLLECTION_WIDE_BREAKPOINT_DP.dp
        PlaylistBackdrop(topColor, midColor, bottomColor, artworkPath = null)
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp + bottomInset),
        ) {
            item(key = "header") {
                PlaylistHeader(
                    name = name,
                    cover = { size ->
                        CollectionCover(
                            icon = collection.icon,
                            size = size.dp,
                            modifier = Modifier.shadow(18.dp, RoundedCornerShape(12.dp)),
                        )
                    },
                    songCount = songs.size,
                    totalDuration = totalDuration,
                    wide = wide,
                    kindLabel = "Collection",
                )
            }
            item(key = "controls") {
                CollectionControls(
                    hasTracks = songs.isNotEmpty(),
                    query = query,
                    onQueryChange = { query = it },
                    onPlay = { model.play(songs, 0) },
                    onShuffle = { model.shuffle(songs) },
                    onPlayNext = { model.playAllNext(songs); toast("Playing ${songs.size} songs next") },
                    onAddToQueue = { model.addAllToQueue(songs); toast("Added ${songs.size} songs to the queue") },
                )
            }
            when {
                songs.isEmpty() -> item(key = "empty") { EmptyCollection(collection) }
                shown.isEmpty() -> item(key = "no-match") {
                    Text(
                        "No songs in $name match “${query.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
                else -> items(shown, key = { it.id }) { song ->
                    val isCurrent = playerState.currentSong?.id == song.id
                    MenuSongRow(
                        song = song,
                        model = model,
                        // Tapping the song that is already playing pauses / resumes it; any other song starts the list there.
                        onPlay = {
                            if (isCurrent) model.player.togglePlayPause()
                            else model.play(shown, shown.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
                        },
                        onAddToPlaylist = { pickerTarget = it },
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        modifier = Modifier.animateItem(),
                        isCurrent = isCurrent,
                        extraItems = if (collection == SmartCollection.FAVORITES) {
                            listOf(RowMenuItem("Remove from Favorites", { model.favorite(song) }))
                        } else {
                            emptyList()
                        },
                    )
                }
            }
        }

        // Back button that stays put while the list scrolls; once the header is gone it becomes a title bar.
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
    }

    PlaylistTargetDialog(pickerTarget, model) { pickerTarget = null }
}

/** Songs whose title, artist or album contains [query] (ignoring case); every song for a blank query. */
internal fun List<Song>.matching(query: String): List<Song> {
    val needle = query.trim()
    if (needle.isEmpty()) return this
    return filter { song ->
        song.title.contains(needle, ignoreCase = true) ||
            song.artistName.orEmpty().contains(needle, ignoreCase = true) ||
            song.albumTitle.orEmpty().contains(needle, ignoreCase = true)
    }
}

/** The same control row as a playlist page: more menu on the left, Shuffle and the big Play button on the right. */
@Composable
private fun CollectionControls(
    hasTracks: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var moreOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                GlassToolbarButton(onClick = { moreOpen = true }, enabled = hasTracks) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Playlist actions")
                }
                GlassDropdownMenu(
                    expanded = moreOpen,
                    onDismissRequest = { moreOpen = false },
                    entries = listOf(
                        GlassMenuEntry("Play Next", onClick = onPlayNext),
                        GlassMenuEntry("Add to Queue", onClick = onAddToQueue),
                    ),
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
                    contentDescription = "Play all",
                    tint = if (hasTracks) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        if (hasTracks) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlaylistFindField(query, onQueryChange, Modifier.weight(1f), placeholder = "Find in list")
            }
        }
    }
}

@Composable
private fun EmptyCollection(collection: SmartCollection) {
    val (title, body) = when (collection) {
        SmartCollection.FAVORITES -> "No favorites yet" to "Tap the heart on any song and it will appear here."
        SmartCollection.RECENTLY_PLAYED -> "Nothing played yet" to "Songs you listen to show up here, newest first."
        SmartCollection.MOST_PLAYED -> "No plays yet" to "Your most played songs appear here as you listen."
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
