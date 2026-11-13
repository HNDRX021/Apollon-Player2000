package com.auralis.music.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auralis.music.core.formatDuration
import com.auralis.music.data.SongEntity
import kotlinx.coroutines.Dispatchers
import java.io.File

@Composable
fun Artwork(
    path: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    animate: Boolean = false
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = path) {
        value = path?.let { p -> kotlinx.coroutines.withContext(Dispatchers.IO) { BitmapFactory.decodeFile(p) } }
    }
    val scale = if (animate) {
        val transition = rememberInfiniteTransition(label = "artwork")
        transition.animateFloat(1f, 1.035f, infiniteRepeatable(tween(4_000), RepeatMode.Reverse), label = "scale")
    } else null
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (scale != null) Modifier.scale(scale.value) else Modifier)
            )
        } else {
            Icon(Icons.Rounded.MusicNote, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun SongRow(
    song: SongEntity,
    onClick: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
    onDetails: () -> Unit = {},
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onDetails)
            .padding(horizontal = 8.dp, vertical = if (compact) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(song.artworkPath, Modifier.size(if (compact) 48.dp else 56.dp), song.title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" • "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(song.durationMs.formatDuration(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        IconButton(onClick = onFavorite) { Icon(if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) }
        IconButton(onClick = onQueue) { Icon(Icons.Rounded.QueueMusic, null) }
    }
}

@Composable
fun MiniPlayer(song: SongEntity, isPlaying: Boolean, progress: Float, onOpen: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    Surface(
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.artworkPath, Modifier.size(50.dp), song.title)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null) }
                IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, null) }
            }
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) IconButton(onClick = action) { Icon(Icons.Rounded.ChevronRight, null) }
    }
}

@Composable
fun AlbumCard(album: String, artist: String, artwork: String?, onClick: () -> Unit) {
    Column(Modifier.width(164.dp).clickable(onClick = onClick)) {
        Artwork(artwork, Modifier.fillMaxWidth().aspectRatio(1f), album)
        Spacer(Modifier.height(8.dp))
        Text(album, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun EmptyState(title: String, body: String, buttonText: String? = null, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.size(86.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Album, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (buttonText != null && onClick != null) {
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onClick) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(buttonText) }
        }
    }
}

@Composable
fun BottomNavigation(current: String, onNavigate: (String) -> Unit, label: (String) -> String) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
        val items = listOf(
            Triple("home", Icons.Rounded.Home, label("home")),
            Triple("library", Icons.Rounded.LibraryMusic, label("library")),
            Triple("search", Icons.Rounded.Search, label("search")),
            Triple("playlists", Icons.Rounded.QueueMusic, label("playlists")),
            Triple("settings", Icons.Rounded.Settings, label("settings"))
        )
        items.forEach { (route, icon, text) ->
            NavigationBarItem(selected = current == route, onClick = { onNavigate(route) }, icon = { Icon(icon, null) }, label = { Text(text) })
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        actions?.invoke()
    }
}

@Composable
fun PlayButton(onClick: () -> Unit, playing: Boolean, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = modifier.size(58.dp).clickable(onClick = onClick)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
        }
    }
}
