package com.auralis.player.ui.screens.player

import com.auralis.player.ui.components.GlassTextButton
import com.auralis.player.ui.components.GlassAlertDialog
import com.auralis.player.ui.components.GlassDropdownMenu
import com.auralis.player.ui.components.GlassMenuEntry
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Speed

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import com.auralis.player.ui.components.LocalEditFileActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.player.R
import com.auralis.player.domain.model.Playlist
import com.auralis.player.domain.model.Song
import com.auralis.player.domain.util.LrcParser
import com.auralis.player.data.lyrics.LyricsLookupResult
import com.auralis.player.playback.NowPlayingUiState
import com.auralis.player.settings.AppSettings
import com.auralis.player.ui.components.ArtworkImage
import com.auralis.player.ui.components.EmptyState
import com.auralis.player.ui.components.GlassIconButton
import com.auralis.player.ui.components.glassTextFieldColors
import com.auralis.player.ui.components.formatDuration
import com.auralis.player.ui.screens.MusicScreensViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Foreground colours. The background is always darkened until white text passes 4.5:1,
// so the player draws light-on-dark whatever the app theme is.
private val OnArt = Color.White
private val OnArtMuted = Color.White.copy(alpha = 0.68f)
private val OnArtTrack = Color.White.copy(alpha = 0.28f)
private val ButtonFill = Color.White.copy(alpha = 0.18f)
private val HeartColor = Color(0xFFFF5A6E)

/**
 * Now Playing, in the style of Apple Music: a background built from the
 * current cover, a large rounded cover, title/artist with favourite and
 * options buttons, a seekable progress bar, previous/play/next, a system
 * volume slider and a bottom bar with Lyrics, Equalizer and Queue.
 */
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenAlbum: (Long) -> Unit = {},
    onOpenArtist: (Long) -> Unit = {},
) {
    val model: MusicScreensViewModel = hiltViewModel()
    val state by model.playerState.collectAsState()
    val settings by model.appSettings.collectAsState(initial = AppSettings())
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val song = state.currentSong
    val motion = rememberMotionEnabled()

    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showLyricsEditor by remember(song?.id) { mutableStateOf(false) }
    var confirmLyricsReplace by remember(song?.id) { mutableStateOf(false) }
    var isFindingLyrics by remember(song?.id) { mutableStateOf(false) }
    var lyricsFeedback by remember(song?.id) { mutableStateOf(LyricsFeedback.None) }
    val lyrics by remember(song?.id) {
        song?.let { model.repository.observeLyrics(it.id) } ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)
    val lyricsScope = rememberCoroutineScope()

    fun findLyrics() {
        val currentSong = song ?: return
        if (isFindingLyrics) return
        lyricsScope.launch {
            isFindingLyrics = true
            lyricsFeedback = LyricsFeedback.None
            try {
                lyricsFeedback = when (model.findLyrics(currentSong)) {
                    is LyricsLookupResult.Found -> LyricsFeedback.Found
                    LyricsLookupResult.NotFound -> LyricsFeedback.NotFound
                    LyricsLookupResult.MissingArtist -> LyricsFeedback.MissingArtist
                    LyricsLookupResult.Failed -> LyricsFeedback.Failed
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                lyricsFeedback = LyricsFeedback.Failed
            } finally {
                isFindingLyrics = false
            }
        }
    }

    // Keep showing the previous theme until the next cover's colours are ready (no flash to neutral).
    var theme by remember { mutableStateOf(NeutralTheme) }
    val artworkPath = song?.artworkUri
    LaunchedEffect(artworkPath) {
        theme = if (artworkPath == null) NeutralTheme else (loadArtworkTheme(artworkPath) ?: NeutralTheme)
    }

    LightSystemBarIcons(enabled = song != null)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 680.dp

        if (song != null) {
            NowPlayingBackground(theme, motion)
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        val contentColor = if (song != null) OnArt else MaterialTheme.colorScheme.onBackground

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            PlayerHandle(onBack = onBack, handleColor = contentColor)

            if (song == null) {
                EmptyState("Nothing playing", "Pick a song from your library to get started.")
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Crossfade(targetState = showLyrics, animationSpec = tween(if (motion) 300 else 0), label = "np_panel") { showingLyrics ->
                        if (showingLyrics) {
                            LyricsPanel(
                                lyrics = lyrics,
                                positionMs = state.positionMs,
                                isFinding = isFindingLyrics,
                                feedback = lyricsFeedback,
                                onEdit = { showLyricsEditor = true },
                                onFind = {
                                    if (lyrics.isNullOrBlank()) findLyrics() else confirmLyricsReplace = true
                                },
                            )
                        } else {
                            ArtworkPage(song, state.isPlaying, motion, model, isRtl)
                        }
                    }
                }
                Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
                TrackInfoRow(
                    song = song,
                    model = model,
                    motion = motion,
                    speed = state.playbackSpeed,
                    onAddToPlaylist = { showPlaylistPicker = true },
                    onSpeed = { showSpeed = true },
                    onSleep = { showSleep = true },
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                )
                Spacer(Modifier.height(if (compact) 8.dp else 18.dp))
                ProgressSection(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    motion = motion,
                    showRemaining = settings.showRemainingTime,
                    onToggleRemaining = { model.setShowRemainingTime(!settings.showRemainingTime) },
                    onSeek = { model.player.seekTo(it) },
                )
                Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
                TransportControls(state = state, model = model, motion = motion, compact = compact)
                Spacer(Modifier.height(if (compact) 4.dp else 14.dp))
                VolumeControl(motion = motion)
                Spacer(Modifier.height(if (compact) 2.dp else 8.dp))
                UtilityBar(
                    lyricsActive = showLyrics,
                    motion = motion,
                    onLyrics = { showLyrics = !showLyrics },
                    onEqualizer = onOpenEqualizer,
                    onQueue = onOpenQueue,
                )
            }
        }
    }

    if (showSpeed) {
        ChoiceDialog("Playback speed", listOf(0.75f, 1f, 1.25f, 1.5f, 2f).map { "${it}×" to it }, onDismiss = { showSpeed = false }) {
            model.player.setPlaybackSpeed(it)
            showSpeed = false
        }
    }
    if (showSleep) {
        GlassAlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("Sleep timer") },
            text = { Text("Choose when playback should stop.") },
            confirmButton = {
                Column {
                    (listOf(settings.defaultSleepTimerMinutes) + listOf(15, 30, 45, 60)).distinct().forEach { minutes ->
                        GlassTextButton(onClick = { model.player.startSleepTimer(minutes); showSleep = false }) { Text("$minutes minutes") }
                    }
                    GlassTextButton(onClick = { model.player.sleepAtEndOfCurrentTrack(); showSleep = false }) { Text("End of current track") }
                    if (state.isSleepTimerActive) GlassTextButton(onClick = { model.player.cancelSleepTimer(); showSleep = false }) { Text("Turn off timer") }
                }
            },
            dismissButton = { GlassTextButton(onClick = { showSleep = false }) { Text("Cancel") } },
        )
    }
    if (showPlaylistPicker && song != null) {
        AddToPlaylistDialog(song = song, model = model, onDismiss = { showPlaylistPicker = false })
    }
    if (showLyricsEditor && song != null) {
        LyricsEditorDialog(
            initialLyrics = lyrics.orEmpty(),
            onDismiss = { showLyricsEditor = false },
            onSave = { edited ->
                model.saveLyrics(song.id, edited.trim().takeIf(String::isNotEmpty))
                showLyricsEditor = false
                lyricsFeedback = LyricsFeedback.Saved
            },
        )
    }
    if (confirmLyricsReplace) {
        GlassAlertDialog(
            onDismissRequest = { confirmLyricsReplace = false },
            title = { Text(stringResource(R.string.np_lyrics_replace_title)) },
            text = { Text(stringResource(R.string.np_lyrics_replace_body)) },
            confirmButton = {
                GlassTextButton(onClick = { confirmLyricsReplace = false; findLyrics() }) {
                    Text(stringResource(R.string.np_lyrics_replace_confirm))
                }
            },
            dismissButton = {
                GlassTextButton(onClick = { confirmLyricsReplace = false }) {
                    Text(stringResource(R.string.search_cancel))
                }
            },
        )
    }
}

private enum class LyricsFeedback { None, Saved, Found, NotFound, MissingArtist, Failed }

/** Artwork and UI animations are always on, unless the system "remove animations" setting is active. */
@Composable
private fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
}

// --- Handle ----------------------------------------------------------------

/** Grabber at the top: tap it or drag down to close. The cast/audio-output button sits at the end. */
@Composable
private fun PlayerHandle(onBack: () -> Unit, handleColor: Color) {
    val closeLabel = stringResource(R.string.np_back)
    val latestBack by rememberUpdatedState(onBack)
    var dragged by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, delta -> dragged += delta },
                    onDragEnd = { if (dragged > 60f) latestBack() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 96.dp, height = 44.dp)
                .clickable(onClickLabel = closeLabel, role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 40.dp, height = 5.dp).clip(CircleShape).background(handleColor.copy(alpha = 0.55f)))
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { AudioOutputButton() }
    }
}

// --- Cover / lyrics --------------------------------------------------------

@Composable
private fun ArtworkPage(song: Song, isPlaying: Boolean, motion: Boolean, model: MusicScreensViewModel, isRtl: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
        val side = min(maxWidth, maxHeight).coerceAtLeast(96.dp)
        // Like Apple Music, the cover relaxes slightly while paused.
        val scale by animateFloatAsState(
            targetValue = if (isPlaying || !motion) 1f else 0.88f,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
            label = "coverScale",
        )
        var horizontalDrag by remember(song.id) { mutableFloatStateOf(0f) }
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier
                .size(side)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .pointerInput(song.id) {
                    val halfWidth = size.width / 2f
                    detectTapGestures(onDoubleTap = { offset ->
                        val current = model.player.uiState.value
                        val leftSide = offset.x < halfWidth
                        val delta = if (leftSide xor isRtl) -10_000L else 10_000L
                        model.player.seekTo((current.positionMs + delta).coerceIn(0L, current.durationMs))
                    })
                }
                .pointerInput(song.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, delta -> horizontalDrag += delta },
                        onDragEnd = {
                            val current = model.player.uiState.value
                            val swipeNext = if (isRtl) horizontalDrag > 72f else horizontalDrag < -72f
                            val swipePrevious = if (isRtl) horizontalDrag < -72f else horizontalDrag > 72f
                            if (swipePrevious && current.hasPrevious) model.player.skipPrevious()
                            else if (swipeNext && current.hasNext) model.player.skipNext()
                            horizontalDrag = 0f
                        },
                        onDragCancel = { horizontalDrag = 0f },
                    )
                },
        ) {
            // Cross-fades when the artwork changes; tracks of one album share a cover and do not flicker.
            Crossfade(targetState = song.artworkUri, animationSpec = tween(if (motion) 400 else 0), label = "cover") { uri ->
                ArtworkImage(uri, Modifier.fillMaxSize(), targetSize = side, shape = shape)
            }
        }
    }
}

@Composable
private fun LyricsPanel(
    lyrics: String?,
    positionMs: Long,
    isFinding: Boolean,
    feedback: LyricsFeedback,
    onEdit: () -> Unit,
    onFind: () -> Unit,
) {
    val lines = remember(lyrics) { lyrics?.let(LrcParser::parse).orEmpty() }
    val currentLine = lines.indexOfLast { it.timeMs <= positionMs }
    val listState = rememberLazyListState()
    LaunchedEffect(currentLine) {
        if (currentLine >= 0) listState.animateScrollToItem((currentLine - 2).coerceAtLeast(0))
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.np_lyrics_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnArt,
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(onClick = onEdit, size = 40.dp) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.np_lyrics_edit))
                }
                GlassIconButton(onClick = onFind, size = 40.dp, enabled = !isFinding) {
                    if (isFinding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = OnArt)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.np_lyrics_find_online))
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                when {
                    lines.isNotEmpty() -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(vertical = 24.dp),
                    ) {
                        itemsIndexed(lines, key = { index, line -> "${line.timeMs}_$index" }) { index, line ->
                            Text(
                                text = line.text.ifBlank { " " },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                                color = if (index == currentLine) OnArt else OnArtMuted.copy(alpha = 0.72f),
                                fontSize = if (index == currentLine) 23.sp else 19.sp,
                                lineHeight = if (index == currentLine) 31.sp else 27.sp,
                                fontWeight = if (index == currentLine) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    !lyrics.isNullOrBlank() -> Text(
                        text = lyrics,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
                        color = OnArt,
                        fontSize = 19.sp,
                        lineHeight = 28.sp,
                        textAlign = TextAlign.Center,
                    )
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FormatQuote, contentDescription = null, tint = OnArtMuted, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.np_lyrics_empty),
                            color = OnArtMuted,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.np_lyrics_hint),
                            color = OnArtMuted.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            val feedbackText = when {
                isFinding -> stringResource(R.string.np_lyrics_searching)
                feedback == LyricsFeedback.Saved -> stringResource(R.string.np_lyrics_saved)
                feedback == LyricsFeedback.Found -> stringResource(R.string.np_lyrics_found)
                feedback == LyricsFeedback.NotFound -> stringResource(R.string.np_lyrics_not_found)
                feedback == LyricsFeedback.MissingArtist -> stringResource(R.string.np_lyrics_missing_artist)
                feedback == LyricsFeedback.Failed -> stringResource(R.string.np_lyrics_lookup_failed)
                else -> null
            }
            if (feedbackText != null) {
                Text(
                    feedbackText,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = OnArtMuted,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LyricsEditorDialog(
    initialLyrics: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(initialLyrics) { mutableStateOf(initialLyrics) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.np_lyrics_editor_title)) },
        text = {
            Column {
                Text(stringResource(R.string.np_lyrics_editor_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_LYRICS_LENGTH) text = it },
                    label = { Text(stringResource(R.string.np_lyrics_field_label)) },
                    minLines = 5,
                    maxLines = 10,
                    shape = RoundedCornerShape(14.dp),
                    colors = glassTextFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                )
            }
        },
        confirmButton = {
            GlassTextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.np_lyrics_save)) }
        },
        dismissButton = {
            GlassTextButton(onClick = onDismiss) { Text(stringResource(R.string.search_cancel)) }
        },
    )
}

private const val MAX_LYRICS_LENGTH = 100_000

// --- Title / artist / favourite / options ----------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackInfoRow(
    song: Song,
    model: MusicScreensViewModel,
    motion: Boolean,
    speed: Float,
    onAddToPlaylist: () -> Unit,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isFavorite by remember(song.id) { model.repository.observeIsFavorite(song.id) }
        .collectAsState(initial = song.isFavorite)
    val heartTint by animateColorAsState(if (isFavorite) HeartColor else OnArt, tween(if (motion) 200 else 0), label = "heart")

    // Title -> the album stored on the track; artist -> that album's album-artist (the only artists
    // that have a page), so a "Artist ft. Guest" credit still opens the right artist.
    val albumId = song.albumId
    val albumArtistId by produceState<Long?>(initialValue = null, song.albumId) {
        value = song.albumId?.let { id -> model.repository.findAlbumArtistId(id) }
    }
    val labelShape = RoundedCornerShape(6.dp)
    val openAlbumLabel = stringResource(R.string.np_open_album)
    val openArtistLabel = stringResource(R.string.np_open_artist)
    val titleLink = Modifier
        .clip(labelShape)
        .clickable(enabled = albumId != null, onClickLabel = openAlbumLabel, role = Role.Button) { albumId?.let(onOpenAlbum) }
        .padding(vertical = 1.dp)
    val artistLink = Modifier
        .clip(labelShape)
        .clickable(enabled = albumArtistId != null, onClickLabel = openArtistLabel, role = Role.Button) { albumArtistId?.let(onOpenArtist) }
        .padding(vertical = 1.dp)
    var menuOpen by remember { mutableStateOf(false) }
    val editActions = LocalEditFileActions.current
    // A title or artist that doesn't fit scrolls sideways (pause, scroll, loop) so the whole text can be read;
    // text that fits stays still. With system animations off it falls back to an ellipsis.
    val marquee = if (motion) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, velocity = 40.dp) else Modifier

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(
            targetState = song.title to song.artistName,
            animationSpec = tween(if (motion) 250 else 0),
            modifier = Modifier.weight(1f),
            label = "trackInfo",
        ) { (title, artist) ->
            Column {
                Text(
                    title,
                    modifier = titleLink.then(marquee),
                    color = OnArt,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = if (motion) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
                Text(
                    artist ?: "Unknown artist",
                    modifier = artistLink.then(marquee),
                    color = OnArtMuted,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = if (motion) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        PressableIcon(
            onClick = { scope.launch { model.repository.setFavorite(song.id, !isFavorite) } },
            description = stringResource(R.string.song_favorite_toggle),
            motion = motion,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(ButtonFill),
        ) {
            Icon(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = heartTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Box {
            PressableIcon(
                onClick = { menuOpen = true },
                description = stringResource(R.string.np_more),
                motion = motion,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(ButtonFill),
            ) {
                Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = OnArt, modifier = Modifier.size(22.dp))
            }
            val speedLabel = if (speed != 1f) "${stringResource(R.string.np_speed)} (${speed}×)" else stringResource(R.string.np_speed)
            GlassDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                entries = listOfNotNull(
                    GlassMenuEntry(stringResource(R.string.song_add_to_playlist), onClick = onAddToPlaylist),
                    song.artistId?.let { artistId ->
                        GlassMenuEntry(
                            stringResource(R.string.media_go_to_artist),
                            onClick = { onOpenArtist(artistId) },
                            dividerBefore = true,
                        )
                    },
                    song.albumId?.let { albumId ->
                        GlassMenuEntry(stringResource(R.string.media_go_to_album), onClick = { onOpenAlbum(albumId) })
                    },
                    GlassMenuEntry(speedLabel, onClick = onSpeed, leadingIcon = Icons.Outlined.Speed),
                    GlassMenuEntry(stringResource(R.string.np_sleep_timer), onClick = onSleep, leadingIcon = Icons.Outlined.Bedtime),
                    GlassMenuEntry(stringResource(R.string.song_open_folder), onClick = { model.openSongFolder(song) }),
                    GlassMenuEntry(stringResource(R.string.song_refresh_metadata), onClick = { model.refreshSongMetadata(song) }),
                    editActions?.let { actions ->
                        GlassMenuEntry("Edit File", onClick = { actions.editSong(song) }, leadingIcon = Icons.Outlined.Edit, dividerBefore = true)
                    },
                ),
            )
        }
    }
}

// --- Progress --------------------------------------------------------------

@Composable
private fun ProgressSection(
    positionMs: Long,
    durationMs: Long,
    motion: Boolean,
    showRemaining: Boolean,
    onToggleRemaining: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownMs = dragFraction?.let { (it * durationMs).toLong() } ?: positionMs

    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        GlassSlider(
            fraction = fraction,
            onChange = { dragFraction = it },
            onCommit = { f ->
                onSeek((f * durationMs).toLong())
                dragFraction = null
            },
            onCancel = { dragFraction = null },
            description = stringResource(R.string.np_position),
            motion = motion,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shownMs), color = OnArtMuted, fontSize = 12.sp)
            Text(
                if (showRemaining) "-${formatDuration((durationMs - shownMs).coerceAtLeast(0L))}" else formatDuration(durationMs),
                color = OnArtMuted,
                fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onToggleRemaining),
            )
        }
    }
}

/**
 * Thin rounded slider (played part solid, remainder translucent). The track
 * thickens while dragging. [onChange] fires continuously with the touched
 * fraction, [onCommit] once when the finger lifts or the bar is tapped.
 */
@Composable
private fun GlassSlider(
    fraction: Float,
    onChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    description: String,
    motion: Boolean,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit = {},
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val latestChange by rememberUpdatedState(onChange)
    val latestCommit by rememberUpdatedState(onCommit)
    val latestCancel by rememberUpdatedState(onCancel)
    val shown = (dragFraction ?: fraction).coerceIn(0f, 1f)
    val trackHeight by animateDpAsState(
        targetValue = if (dragFraction != null) 10.dp else 6.dp,
        animationSpec = tween(if (motion) 150 else 0),
        label = "trackHeight",
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .fillMaxWidth()
                .height(32.dp)
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                    setProgress { target ->
                        val f = target.coerceIn(0f, 1f)
                        latestChange(f)
                        latestCommit(f)
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        latestChange(f)
                        latestCommit(f)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val f = (offset.x / size.width).coerceIn(0f, 1f)
                            dragFraction = f
                            latestChange(f)
                        },
                        onHorizontalDrag = { change, _ ->
                            val f = (change.position.x / size.width).coerceIn(0f, 1f)
                            dragFraction = f
                            latestChange(f)
                            change.consume()
                        },
                        onDragEnd = {
                            dragFraction?.let { latestCommit(it) }
                            dragFraction = null
                        },
                        onDragCancel = {
                            dragFraction = null
                            latestCancel()
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(trackHeight).clip(CircleShape).background(OnArtTrack))
            Box(Modifier.fillMaxWidth(shown).height(trackHeight).clip(CircleShape).background(OnArt))
        }
    }
}

// --- Transport -------------------------------------------------------------

@Composable
private fun TransportControls(state: NowPlayingUiState, model: MusicScreensViewModel, motion: Boolean, compact: Boolean) {
    val playSize = if (compact) 64.dp else 76.dp
    // Transport order stays left-to-right in every language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressableIcon(
                onClick = { model.player.toggleShuffle() },
                description = stringResource(R.string.np_shuffle),
                motion = motion,
                selected = state.shuffleEnabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = null,
                    tint = if (state.shuffleEnabled) OnArt else OnArt.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp),
                )
            }
            PressableIcon(
                onClick = { model.player.skipPrevious() },
                description = stringResource(R.string.np_previous),
                motion = motion,
                enabled = state.hasPrevious,
                modifier = Modifier.size(60.dp),
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = OnArt, modifier = Modifier.size(44.dp))
            }
            PressableIcon(
                onClick = { model.player.togglePlayPause() },
                description = stringResource(R.string.np_play_pause),
                motion = motion,
                modifier = Modifier.size(playSize),
            ) {
                Crossfade(targetState = state.isPlaying, animationSpec = tween(if (motion) 180 else 0), label = "playPause") { playing ->
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = OnArt,
                        modifier = Modifier.size(playSize * 0.85f),
                    )
                }
            }
            PressableIcon(
                onClick = { model.player.skipNext() },
                description = stringResource(R.string.np_next),
                motion = motion,
                enabled = state.hasNext,
                modifier = Modifier.size(60.dp),
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = null, tint = OnArt, modifier = Modifier.size(44.dp))
            }
            PressableIcon(
                onClick = { model.player.cycleRepeatMode() },
                description = stringResource(R.string.np_repeat),
                motion = motion,
                selected = state.repeatMode != 0,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (state.repeatMode == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = null,
                    tint = if (state.repeatMode != 0) OnArt else OnArt.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

// --- Volume ----------------------------------------------------------------

/** Controls the real music-stream volume and follows hardware-button changes. */
@Composable
private fun VolumeControl(motion: Boolean) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    DisposableEffect(audioManager) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (volume == 0) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeDown,
                contentDescription = null,
                tint = OnArtMuted,
                modifier = Modifier.size(20.dp),
            )
            GlassSlider(
                fraction = volume.toFloat() / maxVolume,
                onChange = { f ->
                    val target = (f * maxVolume).roundToInt().coerceIn(0, maxVolume)
                    volume = target
                    runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
                },
                onCommit = {},
                description = stringResource(R.string.np_volume),
                motion = motion,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = OnArtMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// --- Bottom utility bar ----------------------------------------------------

/** Lyrics (left), Equalizer (centre), Queue (right). */
@Composable
private fun UtilityBar(
    lyricsActive: Boolean,
    motion: Boolean,
    onLyrics: () -> Unit,
    onEqualizer: () -> Unit,
    onQueue: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UtilityButton(Icons.Default.FormatQuote, stringResource(R.string.np_tab_lyrics), lyricsActive, motion, onLyrics, Modifier.weight(1f))
        UtilityButton(Icons.Default.Equalizer, stringResource(R.string.eq_open_description), false, motion, onEqualizer, Modifier.weight(1f))
        UtilityButton(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.np_queue), false, motion, onQueue, Modifier.weight(1f))
    }
}

@Composable
private fun UtilityButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    selected: Boolean,
    motion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        PressableIcon(
            onClick = onClick,
            description = description,
            motion = motion,
            selected = selected,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) OnArt else OnArt.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * Round tap target with a pressed state (slight shrink + soft highlight),
 * an optional "selected" pill, a dimmed disabled state and a screen-reader label.
 */
@Composable
private fun PressableIcon(
    onClick: () -> Unit,
    description: String,
    motion: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motion) 0.88f else 1f,
        animationSpec = tween(120),
        label = "pressScale",
    )
    val highlight = when {
        pressed -> 0.20f
        selected -> 0.16f
        else -> 0f
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.35f
            }
            .clip(CircleShape)
            .background(OnArt.copy(alpha = highlight))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// --- Add to playlist -------------------------------------------------------

@Composable
private fun AddToPlaylistDialog(song: Song, model: MusicScreensViewModel, onDismiss: () -> Unit) {
    val playlists by model.playlists.collectAsState(initial = emptyList<Playlist>())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.song_add_to_playlist)) },
        text = {
            if (playlists.isEmpty()) {
                Text(stringResource(R.string.playlist_picker_none))
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        GlassTextButton(
                            onClick = {
                                scope.launch { model.repository.addSongToPlaylist(playlist.id, song.id) }
                                Toast.makeText(context, context.getString(R.string.np_added_to_playlist, playlist.name), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        },
        confirmButton = { GlassTextButton(onClick = onDismiss) { Text(stringResource(R.string.playlists_cancel)) } },
    )
}

// --- System bars -----------------------------------------------------------

/** Forces light status/navigation bar icons while the dark, cover-coloured player is on screen. */
@Composable
private fun LightSystemBarIcons(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val window = view.context.findActivity()?.window
        if (!enabled || window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val wasLightStatus = controller.isAppearanceLightStatusBars
        val wasLightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = wasLightStatus
            controller.isAppearanceLightNavigationBars = wasLightNav
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// --- Background ------------------------------------------------------------

/** Cross-fades between cover themes so a track change never flashes or jumps. */
@Composable
private fun NowPlayingBackground(theme: ArtworkTheme, motion: Boolean) {
    Crossfade(
        targetState = theme,
        animationSpec = tween(if (motion) 1000 else 0),
        modifier = Modifier.fillMaxSize(),
        label = "bgTheme",
    ) { current -> ThemeLayers(current, motion) }
}

/**
 * Three layers: (1) the cover-coloured vertical gradient with two slowly
 * drifting glows, (2) a blurred, faint copy of the cover itself, and (3) a
 * dark scrim top and bottom that keeps white text and controls readable.
 */
@Composable
private fun ThemeLayers(theme: ArtworkTheme, motion: Boolean) {
    val transition = rememberInfiniteTransition(label = "bgDrift")
    // Kept as a State (no `by`) and only read inside the Canvas below, i.e. while drawing: the 20 s drift
    // then repaints just the gradient canvas each frame. Reading it here in composition re-ran this whole
    // layer (and its blurred cover) 60+ times a second for as long as Now Playing was open. Same picture.
    val drift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val top = theme.colors[0]
    val middle = theme.colors[1]
    val bottom = theme.colors[2]

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val p = if (motion) drift.value else 0.5f
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(top, middle, bottom)))
            drawRect(
                Brush.radialGradient(
                    colors = listOf(theme.glow.copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(w * (0.15f + 0.7f * p), h * 0.20f),
                    radius = w * 1.0f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(lerp(middle, Color.White, 0.18f).copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(w * (0.85f - 0.6f * p), h * 0.62f),
                    radius = w * 0.9f,
                ),
            )
        }
        theme.blurred?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.40f,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize().blur(48.dp, BlurredEdgeTreatment.Unbounded),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.40f))))
        }
    }
}
