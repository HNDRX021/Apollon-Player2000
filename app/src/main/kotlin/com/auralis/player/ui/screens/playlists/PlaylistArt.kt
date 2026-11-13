package com.auralis.player.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auralis.player.ui.components.ArtworkImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A playlist's cover. Every playlist is drawn on the accent colour chosen in Settings
 * (`MaterialTheme.colorScheme.primary`, the same colour the rest of the UI uses, so it follows the
 * setting live) with a white-on-accent note glyph. Nothing is taken from the playlist's tracks.
 * The one exception is a picture the user chose themselves for this playlist ([customArtwork],
 * set through "Choose artwork"): that deliberate choice is kept.
 */
@Composable
internal fun PlaylistCover(
    customArtwork: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    if (customArtwork != null) {
        ArtworkImage(customArtwork, modifier.size(size), targetSize = size, shape = shape)
    } else {
        AccentCover(Icons.Filled.MusicNote, size, modifier, shape)
    }
}

/** The tile of a built-in collection (Favorites, Recently Played, Most Played): the same accent cover with its own glyph. */
@Composable
internal fun CollectionCover(
    icon: ImageVector,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) = AccentCover(icon, size, modifier, shape)

/** The one playlist cover: a flat tile in the current accent colour with a glyph in the colour made for text on it. */
@Composable
private fun AccentCover(
    icon: ImageVector,
    size: Dp,
    modifier: Modifier,
    shape: Shape,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(scheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(size * 0.46f))
    }
}

/**
 * The header gradient of a playlist page: three dark stops (top, middle, bottom) of the accent colour's
 * hue, darkened so white text stays readable. Follows the accent live; no artwork is read.
 */
@Composable
internal fun rememberAccentPalette(): List<Color> {
    val accent = MaterialTheme.colorScheme.primary
    return remember(accent) { accentPalette(accent) }
}

/** Three dark stops of [accent]'s hue, the same tone structure the page backdrops were built for. */
internal fun accentPalette(accent: Color): List<Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
    val saturation = hsv[1].coerceAtMost(0.7f)
    fun tone(saturationFactor: Float, value: Float) =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], saturation * saturationFactor, value)))
    return listOf(tone(1.0f, 0.46f), tone(0.9f, 0.32f), tone(0.8f, 0.17f))
}

/** Three dark stops of one hue, picked from [seed] so the same name always gets the same colours. */
internal fun fallbackPalette(seed: String): List<Color> {
    val hue = ((seed.lowercase().hashCode() and 0x7fffffff) % 360).toFloat()
    fun tone(saturation: Float, value: Float) =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    return listOf(tone(0.55f, 0.46f), tone(0.50f, 0.32f), tone(0.45f, 0.17f))
}

/**
 * The three gradient colours of a playlist page's backdrop, from the cover [palette]: used almost as-is in dark
 * themes (they are already darkened for light text) and washed toward the page background in light themes so
 * dark text stays readable. The same treatment the album page gives its cover.
 */
internal fun backdropStops(palette: List<Color>, background: Color, isDark: Boolean): List<Color> =
    if (isDark) {
        listOf(lerp(background, palette[0], 0.85f), lerp(background, palette[1], 0.50f), lerp(background, palette[2], 0.60f))
    } else {
        listOf(lerp(background, palette[0], 0.42f), lerp(background, palette[1], 0.24f), lerp(background, palette[0], 0.10f))
    }

/**
 * Full-screen backdrop of a playlist page, drawn behind the whole list: an accent-tinted gradient. When the
 * user picked their own picture for the playlist ([artworkPath], null otherwise) it is shown large and softly
 * blurred in the upper part and fades into the gradient further down, so the tracks always sit on a calm,
 * readable surface. The blur comes from decoding the picture tiny and scaling it up, which works on every
 * Android version.
 */
@Composable
internal fun PlaylistBackdrop(top: Color, mid: Color, bottom: Color, artworkPath: String?, modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val art by produceState<ImageBitmap?>(initialValue = null, artworkPath) {
        value = artworkPath?.let { path -> withContext(Dispatchers.IO) { loadTinyBitmap(path) } }
    }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(0f to top, 0.45f to mid, 1f to bottom))) {
        art?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (isDark) 0.42f else 0.30f,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.30f to Color.Transparent,
                        0.75f to bottom.copy(alpha = 0.92f),
                        1f to bottom,
                    ),
                ),
            )
        }
    }
}

/** The picture at [path] decoded to roughly 100 px, for [PlaylistBackdrop]; null when it can't be read. */
private fun loadTinyBitmap(path: String): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 100 && bounds.outHeight / (sample * 2) >= 100) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
