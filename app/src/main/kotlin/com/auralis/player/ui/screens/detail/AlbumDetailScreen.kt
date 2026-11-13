package com.auralis.player.ui.screens.detail

import com.auralis.player.ui.components.GlassToolbarButton
import com.auralis.player.ui.components.GlassDropdownMenu
import com.auralis.player.ui.components.GlassMenuEntry

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import com.auralis.player.ui.components.LocalEditFileActions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.player.R
import com.auralis.player.domain.model.Song
import com.auralis.player.domain.util.albumTotalDurationMs
import com.auralis.player.ui.components.ArtworkImage
import com.auralis.player.ui.components.albumDurationText
import com.auralis.player.ui.components.albumReleaseDateText
import com.auralis.player.ui.components.EmptyState
import com.auralis.player.ui.screens.MusicScreensViewModel
import com.auralis.player.ui.screens.MenuSongRow
import com.auralis.player.ui.screens.PlaylistTarget
import com.auralis.player.ui.screens.PlaylistTargetDialog
import com.auralis.player.ui.screens.player.extractPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat

/**
 * Album page: a cover-tinted gradient that fills the top of the screen and fades into the app
 * background, a pinned back bar that fills in as the header scrolls away, the header (centred
 * cover, bold title, album-artist chip, "Album • year" line, action row with a big play button)
 * and a track list with a track-number column and a per-track overflow menu.
 */
@Composable
fun AlbumDetailScreen(onBack: () -> Unit, onOpenArtist: (Long) -> Unit = {}, onOpenAlbum: (Long) -> Unit = {}, bottomInset: Dp = 0.dp) {
    val model: MusicScreensViewModel = hiltViewModel()
    val album by model.album.collectAsState(initial = null)
    val songs by model.albumSongs.collectAsState(initial = emptyList())
    val artists by model.artists.collectAsState(initial = emptyList())
    val playerState by model.playerState.collectAsState()
    val editActions = LocalEditFileActions.current
    var playlistTarget by remember { mutableStateOf<PlaylistTarget?>(null) }

    val current = album
    val background = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val isDark = background.luminance() < 0.5f

    val palette by produceState<List<Color>?>(initialValue = null, current?.artworkUri) {
        value = current?.artworkUri?.let { path ->
            withContext(Dispatchers.IO) { runCatching { extractPalette(path) }.getOrNull() }
        }
    }
    val topTarget: Color
    val midTarget: Color
    val bottomTarget: Color
    val colors = palette
    if (colors != null && colors.size >= 3) {
        // Stops are already darkened for white text; in dark mode they are used almost as-is, in
        // light mode they are washed toward the page background so dark text stays readable.
        if (isDark) {
            topTarget = lerp(background, colors[0], 0.85f)
            midTarget = lerp(background, colors[1], 0.50f)
            bottomTarget = lerp(background, colors[2], 0.60f)
        } else {
            topTarget = lerp(background, colors[0], 0.42f)
            midTarget = lerp(background, colors[1], 0.24f)
            bottomTarget = lerp(background, colors[0], 0.10f)
        }
    } else {
        // No artwork (or extraction failed): a neutral grey wash, no accent colour.
        val ink = MaterialTheme.colorScheme.onBackground
        topTarget = lerp(background, ink, if (isDark) 0.16f else 0.07f)
        midTarget = lerp(background, ink, if (isDark) 0.08f else 0.035f)
        bottomTarget = background
    }
    val topColor by animateColorAsState(topTarget, tween(500), label = "albumTop")
    val midColor by animateColorAsState(midTarget, tween(500), label = "albumMid")
    val bottomColor by animateColorAsState(bottomTarget, tween(500), label = "albumBottom")

    val albumArtist = artists.firstOrNull { it.id == current?.albumArtistId }
    val artistName = albumArtist?.name ?: songs.firstOrNull()?.artistName
    val releaseText = albumReleaseDateText(current)
    val trackCount = if (songs.isNotEmpty()) songs.size else current?.trackCount ?: 0
    val durationText = albumDurationText(albumTotalDurationMs(songs))

    // Track number shown in the list: the tag when present, otherwise the position within the disc.
    val displayNumbers = remember(songs) {
        val perDisc = HashMap<Int, Int>()
        songs.map { song ->
            val ordinal = (perDisc[song.discNumber] ?: 0) + 1
            perDisc[song.discNumber] = ordinal
            song.trackNumber?.takeIf { it > 0 } ?: ordinal
        }
    }
    val numberFormat = remember { NumberFormat.getIntegerInstance().apply { isGroupingUsed = false } }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val fadeStartPx = with(density) { 300.dp.toPx() }
    val fadeLengthPx = with(density) { 70.dp.toPx() }
    val scrollPx by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100_000f
        }
    }
    val barAlpha by remember { derivedStateOf { ((scrollPx - fadeStartPx) / fadeLengthPx).coerceIn(0f, 1f) } }

    Box(Modifier.fillMaxSize()) {
        // Page background: one opaque, cover-derived gradient over the whole screen (it also hides the
        // app wallpaper, so no image ever shows behind the tracks). It stays put while the list scrolls
        // and ends in a cover-tinted dark/light tone, so there is no hard edge anywhere.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to topColor, 0.45f to midColor, 1f to bottomColor)),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = bottomInset + 16.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                    Spacer(Modifier.height(56.dp)) // room for the pinned back bar
                    if (current != null) {
                        val coverShape = RoundedCornerShape(4.dp)
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
                            ArtworkImage(
                                current.artworkUri,
                                Modifier
                                    .fillMaxWidth(0.63f)
                                    .aspectRatio(1f)
                                    .shadow(20.dp, coverShape),
                                targetSize = 300.dp,
                                shape = coverShape,
                            )
                        }
                        Text(
                            current.title,
                            fontSize = 30.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp),
                        )
                        Row(
                            Modifier
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = albumArtist != null) { albumArtist?.let { onOpenArtist(it.id) } },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtworkImage(albumArtist?.artworkUri, Modifier.size(26.dp), targetSize = 26.dp, shape = CircleShape)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                artistName ?: "Unknown artist",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            listOfNotNull(stringResource(R.string.album_kind), releaseText).joinToString(" • "),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        )
                        Text(
                            listOfNotNull(
                                pluralStringResource(R.plurals.album_track_count, trackCount, trackCount),
                                durationText,
                            ).joinToString(" • "),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
                        )
                        val playingHere = playerState.currentSong?.albumId == current.id
                        AlbumActionRow(
                            isFavorite = current.isFavorite,
                            allTracksFavorite = songs.isNotEmpty() && songs.all { it.isFavorite },
                            playingHere = playingHere,
                            isPlaying = playerState.isPlaying,
                            shuffleEnabled = playerState.shuffleEnabled,
                            hasSongs = songs.isNotEmpty(),
                            onAddAlbumToFavorites = { model.addAlbumToFavorites(current.id) },
                            onAddAlbumToPlaylist = { playlistTarget = PlaylistTarget.OfAlbum(current) },
                            onPlayNext = { model.playAllNext(songs) },
                            onAddToQueue = { model.addAllToQueue(songs) },
                            // The button mirrors the player's real shuffle mode. While this album is playing,
                            // or shuffle is already on, it toggles that mode; otherwise it starts the album shuffled.
                            onShuffle = {
                                if (playingHere || playerState.shuffleEnabled) model.player.toggleShuffle()
                                else model.shuffle(songs)
                            },
                            onPlay = { model.play(songs) },
                            onTogglePlayPause = { model.player.togglePlayPause() },
                            onEditFile = editActions?.let { actions -> { actions.editAlbum(current) } },
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            if (current != null && songs.isEmpty()) {
                item { EmptyState("No tracks found", "This album has no available songs.") }
            } else {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    Column {
                        val showDiscHeader = (current?.discCount ?: 1) > 1 &&
                            (index == 0 || songs[index - 1].discNumber != song.discNumber)
                        if (showDiscHeader) {
                            Text(
                                stringResource(R.string.album_disc_header, song.discNumber),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        MenuSongRow(
                            song = song,
                            model = model,
                            onPlay = { model.play(songs, index) },
                            onAddToPlaylist = { playlistTarget = it },
                            isCurrent = playerState.currentSong?.id == song.id,
                            showAlbum = false,
                            onOpenArtist = onOpenArtist,
                            onOpenAlbum = onOpenAlbum,
                            leading = {
                                // The track number takes the artwork's place: every track shares the album's cover.
                                Text(
                                    numberFormat.format(displayNumbers.getOrElse(index) { index + 1 }),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                                    color = if (playerState.currentSong?.id == song.id) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        }
        // Pinned bar: the back arrow is always reachable; the tinted bar and the title fade in
        // once the header has scrolled away.
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(topColor.copy(alpha = barAlpha)) }
                .statusBarsPadding()
                .height(56.dp)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassToolbarButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_back), modifier = Modifier.size(28.dp))
            }
            Text(
                current?.title.orEmpty(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp).graphicsLayer { alpha = barAlpha },
            )
        }
    }
    PlaylistTargetDialog(playlistTarget, model) { playlistTarget = null }
}

@Composable
private fun AlbumActionRow(
    isFavorite: Boolean,
    allTracksFavorite: Boolean,
    playingHere: Boolean,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    hasSongs: Boolean,
    onAddAlbumToFavorites: () -> Unit,
    onAddAlbumToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onEditFile: (() -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    var menuOpen by remember { mutableStateOf(false) }
    var plusMenuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The Plus button opens a menu for the WHOLE album (never just the playing track): add every track to
        // Favorites, or add them all to a playlist (the shared picker, in track order, duplicates skipped).
        // It shows the accent check once the album is a favorite, like before.
        Box {
            GlassToolbarButton(onClick = { plusMenuOpen = true }, modifier = Modifier.size(48.dp)) {
                if (isFavorite) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Add album to Favorites or a playlist",
                        tint = primary,
                        modifier = Modifier.size(26.dp),
                    )
                } else {
                    Icon(
                        Icons.Outlined.AddCircleOutline,
                        contentDescription = "Add album to Favorites or a playlist",
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            GlassDropdownMenu(
                expanded = plusMenuOpen,
                onDismissRequest = { plusMenuOpen = false },
                entries = listOf(
                    GlassMenuEntry(
                        "Add Album to Favorites",
                        onClick = onAddAlbumToFavorites,
                        leadingIcon = if (allTracksFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        highlighted = allTracksFavorite,
                        enabled = hasSongs,
                    ),
                    GlassMenuEntry(
                        "Add Album to Playlist",
                        onClick = onAddAlbumToPlaylist,
                        leadingIcon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                        enabled = hasSongs,
                    ),
                ),
            )
        }
        Box {
            GlassToolbarButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.np_more), modifier = Modifier.size(24.dp))
            }
            GlassDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                entries = listOfNotNull(
                    GlassMenuEntry(stringResource(R.string.details_play_next), onClick = onPlayNext, enabled = hasSongs),
                    GlassMenuEntry(stringResource(R.string.details_add_to_queue), onClick = onAddToQueue, enabled = hasSongs),
                    onEditFile?.let { edit ->
                        GlassMenuEntry("Edit File", onClick = edit, leadingIcon = Icons.Outlined.Edit, enabled = hasSongs, dividerBefore = true)
                    },
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        // Same default look as the other icon buttons in this row; primary tint + soft pill when shuffle is on.
        // IconToggleButton supplies the ripple, hover, focus and pressed feedback.
        IconToggleButton(
            checked = shuffleEnabled,
            onCheckedChange = { onShuffle() },
            enabled = hasSongs,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.iconToggleButtonColors(
                contentColor = LocalContentColor.current,
                checkedContentColor = primary,
                checkedContainerColor = primary.copy(alpha = 0.16f),
            ),
        ) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = stringResource(R.string.action_shuffle),
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (hasSongs) primary else primary.copy(alpha = 0.4f))
                .clickable(enabled = hasSongs) { if (playingHere) onTogglePlayPause() else onPlay() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playingHere && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.action_play),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
