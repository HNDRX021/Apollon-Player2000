package com.auralis.player.ui.screens.player

import com.auralis.player.ui.components.GlassToolbarButton
import com.auralis.player.ui.components.GlassTextButton
import com.auralis.player.ui.components.GlassAlertDialog

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import com.auralis.player.settings.AppSettings
import com.auralis.player.ui.navigation.BottomPanelHeight
import com.auralis.player.ui.navigation.BottomPanelSurface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import com.auralis.player.domain.model.Song
import com.auralis.player.ui.components.ArtworkImage
import com.auralis.player.ui.components.EmptyState
import com.auralis.player.ui.components.ScreenHeader
import com.auralis.player.ui.screens.MusicScreensViewModel
import com.auralis.player.ui.screens.MenuSongRow
import com.auralis.player.ui.screens.PlaylistTarget
import com.auralis.player.ui.screens.PlaylistTargetDialog
import com.auralis.player.ui.components.RowMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// The full-screen player (NowPlayingScreen) lives in NowPlayingScreen.kt.

@Composable
fun MiniPlayer(onOpenNowPlaying: () -> Unit) {
    val model: MusicScreensViewModel = hiltViewModel()
    // The mini player sits under almost every screen, so it must not be recomposed by every position tick:
    // [state] is the player state with the position zeroed (it only changes with the song, play/pause, the
    // queue...), and the live position is read separately, inside the progress bar's draw lambda only.
    val state by remember(model) {
        model.playerState.map { it.copy(positionMs = 0L) }.distinctUntilChanged()
    }.collectAsState(initial = model.playerState.value.copy(positionMs = 0L))
    val progressState = model.playerState.collectAsState()
    val settings by model.appSettings.collectAsState(initial = AppSettings())
    val song = state.currentSong

    // Colours come from the current cover. The previous theme stays until the next one is ready, so a
    // track change never flashes; tracks of one album share a cover path and a cache, so nothing is
    // recomputed. No artwork (or a failed extraction) falls back to the panel's normal look.
    val artworkPath = song?.artworkUri
    val artworkTheme by produceState<ArtworkTheme?>(initialValue = null, artworkPath) {
        value = artworkPath?.let { loadArtworkTheme(it) }
    }
    val scheme = MaterialTheme.colorScheme
    val themed = artworkTheme
    // Darkened a bit further than the Now Playing stops so even the muted white text keeps >= 4.5:1.
    val targetStart = themed?.let { lerp(it.colors[0], Color.Black, 0.25f).copy(alpha = 0.95f) }
        ?: lerp(scheme.surface, scheme.primary, 0.22f).copy(alpha = 0.80f)
    val targetEnd = themed?.let { lerp(it.colors[1], Color.Black, 0.25f).copy(alpha = 0.95f) }
        ?: scheme.surface.copy(alpha = 0.76f)
    val targetContent = if (themed != null) Color.White else scheme.onSurface
    val gradientStart by animateColorAsState(targetStart, tween(450), label = "miniStart")
    val gradientEnd by animateColorAsState(targetEnd, tween(450), label = "miniEnd")
    val content by animateColorAsState(targetContent, tween(450), label = "miniContent")

    if (song == null) return
    // Previous either goes back in the queue (shuffle order included, handled by the player) or,
    // when there is nothing before this track, restarts it if that setting is on.
    val canPrevious = state.hasPrevious || settings.restartTrackOnPrevious
    // Same width, height, corner radius and shadow as the navigation bar below it.
    BottomPanelSurface(
        Modifier.padding(top = 8.dp),
        containerColor = Color.Transparent,
        contentColor = content,
        borderColor = content.copy(alpha = 0.14f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(BottomPanelHeight)
                .background(Brush.horizontalGradient(listOf(gradientStart, gradientEnd))),
        ) {
            Row(
                Modifier.weight(1f).fillMaxWidth().clickable(onClick = onOpenNowPlaying).padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(song.artworkUri, Modifier.size(44.dp), targetSize = 44.dp, shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(song.artistName ?: "Unknown artist", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.78f))
                }
                GlassToolbarButton(onClick = { model.player.skipPrevious() }, enabled = canPrevious, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(com.auralis.player.R.string.np_previous))
                }
                GlassToolbarButton(onClick = { model.player.togglePlayPause() }, modifier = Modifier.size(40.dp)) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = stringResource(com.auralis.player.R.string.np_play_pause))
                }
                GlassToolbarButton(onClick = { model.player.skipNext() }, enabled = state.hasNext, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(com.auralis.player.R.string.np_next))
                }
            }
            LinearProgressIndicator(
                progress = {
                    val live = progressState.value
                    if (live.durationMs > 0) (live.positionMs.toFloat() / live.durationMs).coerceIn(0f, 1f) else 0f
                },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = content,
                trackColor = content.copy(alpha = 0.24f),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
internal fun AudioOutputButton() {
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .build()
    }
    AndroidView(
        modifier = Modifier.size(44.dp),
        factory = { context: Context ->
            MediaRouteButton(context).apply {
                contentDescription = context.getString(com.auralis.player.R.string.audio_output_selector)
                routeSelector = selector
            }
        },
    )
}

@Composable
internal fun <T> ChoiceDialog(title: String, choices: List<Pair<String, T>>, onDismiss: () -> Unit, onChoose: (T) -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { (label, value) -> GlassTextButton(onClick = { onChoose(value) }) { Text(label) } }
            }
        },
        confirmButton = { GlassTextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun QueueScreen(onBack: () -> Unit, onOpenAlbum: (Long) -> Unit = {}, onOpenArtist: (Long) -> Unit = {}) {
    val model: MusicScreensViewModel = hiltViewModel()
    val state by model.playerState.collectAsState()
    var playlistTarget by remember { mutableStateOf<PlaylistTarget?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Queue",
            subtitle = "${state.queue.size} songs",
            onBack = onBack,
            action = { GlassTextButton(onClick = { model.player.clearQueue() }, enabled = state.queue.isNotEmpty()) { Text("Clear") } },
        )
        if (state.queue.isEmpty()) {
            EmptyState("Queue is empty", "Choose a song from your library to start listening.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(state.queue, key = { index, song -> "${song.id}_$index" }) { index, song ->
                    val isCurrent = index == state.currentIndex
                    MenuSongRow(
                        song = song,
                        model = model,
                        onPlay = { model.player.skipToQueueItem(index) },
                        onAddToPlaylist = { playlistTarget = it },
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        isCurrent = isCurrent,
                        leading = {
                            // The position in the queue takes the artwork's slot.
                            Text(
                                "${index + 1}",
                                maxLines = 1,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        extraItems = buildList {
                            if (index > 0) add(RowMenuItem("Move up", { model.player.moveQueueItem(index, index - 1) }))
                            if (index < state.queue.lastIndex) add(RowMenuItem("Move down", { model.player.moveQueueItem(index, index + 1) }))
                            add(RowMenuItem("Remove from queue", { model.player.removeQueueItem(index) }))
                        },
                    )
                }
            }
        }
    }
    PlaylistTargetDialog(playlistTarget, model) { playlistTarget = null }
}
