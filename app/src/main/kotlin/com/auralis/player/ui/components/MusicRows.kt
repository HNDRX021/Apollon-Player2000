package com.auralis.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.auralis.player.domain.model.Album
import com.auralis.player.domain.model.Artist
import com.auralis.player.domain.model.Song

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassIconButton(onClick = onBack, size = 44.dp, contentColor = MaterialTheme.colorScheme.onSurface) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action?.invoke()
    }
}

@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/**
 * THE song row of the whole app: artwork (or [leading] in its place), title, "Artist · Album", a favorite
 * heart and a three-dot menu, side by side in one row, with a subtle divider underneath. Every list that
 * shows songs (Home, Library, Search, albums, artists, playlists, the queue) goes through this one layout;
 * the menu content comes from [com.auralis.player.ui.screens.MenuSongRow], which builds the same actions
 * everywhere. Tapping the row plays the song.
 *
 * [leading] replaces the artwork (an album page shows the track number there), [dragHandle] goes before it
 * (playlist reordering) and [isCurrent] colors the title with the accent. Nothing else varies.
 */
@Composable
fun ShowcaseSongRow(
    song: Song,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    menuItems: List<RowMenuItem>,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    dragHandle: (@Composable () -> Unit)? = null,
    showAlbum: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onPlay)
                .padding(start = if (dragHandle != null) 4.dp else 16.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dragHandle?.invoke()
            if (leading != null) {
                Box(Modifier.size(SongArtworkSize), contentAlignment = Alignment.Center) { leading() }
            } else {
                ArtworkImage(song.artworkUri, Modifier.size(SongArtworkSize), targetSize = SongArtworkSize)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) scheme.primary else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        song.artistName?.takeIf(String::isNotBlank),
                        if (showAlbum) song.albumTitle?.takeIf(String::isNotBlank) else null,
                    ).joinToString(" · ").ifBlank { "Unknown artist" },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GlassIconButton(onClick = onFavorite, size = 40.dp) {
                Icon(
                    if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (song.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (song.isFavorite) scheme.primary else scheme.onSurfaceVariant,
                )
            }
            RowMenuButton(items = menuItems, contentDescription = "Song actions")
        }
        HorizontalDivider(Modifier.padding(start = 72.dp), color = scheme.outlineVariant.copy(alpha = 0.35f))
    }
}

private val SongArtworkSize = 44.dp

/**
 * One entry of a [RowMenuButton]. [trailingIcon] adds an icon to the entry (the Library's
 * "Favorite" shows a heart; the glass menu draws it in the icon column at the start); [highlighted] tints
 * it with the accent colour, e.g. while the item is on.
 */
class RowMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val trailingIcon: ImageVector? = null,
    val highlighted: Boolean = false,
    /** An icon before the label (used by Edit File). */
    val leadingIcon: ImageVector? = null,
    /** Draws a subtle divider above this entry, to set it apart as its own section. */
    val dividerBefore: Boolean = false,
)

/**
 * The three-dot button of a list row or card, opening a floating glass menu ([GlassDropdownMenu]) with
 * [items] in the given order. Icons for the well-known actions are picked automatically.
 */
@Composable
fun RowMenuButton(
    items: List<RowMenuItem>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 40.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        GlassIconButton(onClick = { expanded = true }, size = buttonSize) {
            Icon(Icons.Default.MoreVert, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GlassDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            entries = items.map { item ->
                GlassMenuEntry(
                    label = item.label,
                    onClick = item.onClick,
                    leadingIcon = item.leadingIcon,
                    trailingIcon = item.trailingIcon,
                    highlighted = item.highlighted,
                    destructive = item.label.startsWith("Delete", ignoreCase = true),
                    dividerBefore = item.dividerBefore,
                )
            },
        )
    }
}

@Composable
fun AlbumTile(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier, onFavorite: (() -> Unit)? = null) {
    Column(modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(8.dp)) {
        Box {
            ArtworkImage(album.artworkUri, Modifier.fillMaxWidth().height(156.dp), targetSize = 156.dp, shape = MaterialTheme.shapes.medium)
            if (onFavorite != null) {
                GlassToolbarButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(if (album.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = stringResource(if (album.isFavorite) com.auralis.player.R.string.details_unfavorite_album else com.auralis.player.R.string.details_favorite_album), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${album.trackCount} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier, onFavorite: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(artist.artworkUri, Modifier.size(50.dp).clip(CircleShape), targetSize = 50.dp, shape = CircleShape)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Artist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onFavorite != null) {
            GlassIconButton(onClick = onFavorite, size = 44.dp) {
                Icon(if (artist.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = stringResource(if (artist.isFavorite) com.auralis.player.R.string.details_unfavorite_artist else com.auralis.player.R.string.details_favorite_artist), tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * The Play / Shuffle style button: accent-tinted glass by default, a quiet translucent surface when
 * [secondary] is set (use it for the second of two side-by-side actions).
 */
@Composable
fun PlayAction(
    onClick: () -> Unit,
    label: String = "Play",
    icon: ImageVector = Icons.Default.PlayArrow,
    compact: Boolean = false,
    secondary: Boolean = false,
) {
    GlassButton(
        onClick = onClick,
        style = if (secondary) GlassButtonStyle.Secondary else GlassButtonStyle.Primary,
        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 20.dp, vertical = if (compact) 7.dp else 10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = if (compact) Modifier.size(16.dp) else Modifier)
        Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
        Text(label, style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(totalSeconds / 60, seconds)
}
