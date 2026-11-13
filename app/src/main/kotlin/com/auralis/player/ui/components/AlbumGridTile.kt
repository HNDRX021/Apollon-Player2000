package com.auralis.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auralis.player.R
import com.auralis.player.domain.model.Album

/**
 * Flat album card for grids: a square, square-cornered cover (no radius, no border, no
 * shadow) with the track count in a translucent glass label at its bottom-right corner,
 * then [line1] (bold) and an optional [line2] underneath. The Library uses the album artist
 * as [line1] and the album title as [line2]; the artist page uses the title and the year.
 * An optional [menu] (the Library's three-dot button) sits at the end of the text block, next to
 * the names. It is declared before [onFavorite] so a trailing lambda still means the favorite heart.
 */
@Composable
fun AlbumGridTile(
    album: Album,
    line1: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    line2: String? = null,
    menu: (@Composable () -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
) {
    Column(modifier.clickable(onClick = onClick)) {
        // The label sits at the physical bottom-right in every language, so the cover is laid out LTR.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                ArtworkImage(album.artworkUri, Modifier.fillMaxSize(), targetSize = 220.dp, shape = RectangleShape)
                if (onFavorite != null) {
                    GlassToolbarButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(
                            if (album.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(
                                if (album.isFavorite) R.string.details_unfavorite_album else R.string.details_favorite_album,
                            ),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                TrackCountLabel(album.trackCount, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    line1,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (!line2.isNullOrBlank()) {
                    Text(
                        line2,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            menu?.invoke()
        }
    }
}

/**
 * Translucent "glass" label: a dark tint so white text stays readable on any cover,
 * a faint top-lit sheen and a hairline edge, with the artwork still visible through it.
 * Compose has no backdrop blur, so the frosted look is approximated with tint + sheen.
 */
@Composable
private fun TrackCountLabel(count: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        pluralStringResource(R.plurals.album_track_count, count, count),
        color = Color.White,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.34f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))))
            .border(0.5.dp, Color.White.copy(alpha = 0.30f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
