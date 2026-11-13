package com.auralis.player.ui.screens.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything the Now Playing background needs from one cover.
 *
 * [colors] are the top / middle / bottom stops of the base gradient, already
 * darkened until white text on them passes WCAG AA (4.5:1). [glow] tints the
 * slowly drifting highlights, and [blurred] is a 32px copy of the cover that
 * is blurred and laid faintly over the gradient.
 */
internal class ArtworkTheme(
    val colors: List<Color>,
    val glow: Color,
    val blurred: ImageBitmap?,
)

/** Used when a song has no artwork or the colours cannot be extracted. */
internal val NeutralTheme = ArtworkTheme(
    colors = listOf(Color(0xFF4A4A52), Color(0xFF2E2E34), Color(0xFF16161A)),
    glow = Color(0xFF6A6A74),
    blurred = null,
)

private val themeCache = LruCache<String, ArtworkTheme>(24)

private fun cacheKey(path: String): String = "$path@${File(path).lastModified()}"

/** Returns the (cached) theme for the cover at [path], or null if it cannot be decoded. */
internal suspend fun loadArtworkTheme(path: String): ArtworkTheme? {
    val key = cacheKey(path)
    themeCache.get(key)?.let { return it }
    val theme = withContext(Dispatchers.Default) {
        runCatching { buildArtworkTheme(path) }.getOrNull()
    } ?: return null
    themeCache.put(key, theme)
    return theme
}

/** Blocking variant for callers that already run off the main thread (the album page). */
internal fun extractPalette(path: String): List<Color>? {
    val key = cacheKey(path)
    themeCache.get(key)?.let { return it.colors }
    val theme = runCatching { buildArtworkTheme(path) }.getOrNull() ?: return null
    themeCache.put(key, theme)
    return theme.colors
}

/**
 * Samples the cover once and builds the palette:
 *  - pixels are grouped into 12 hue buckets weighted by saturation and brightness,
 *  - the strongest bucket is the dominant colour, a clearly different hue (if any) the secondary,
 *  - near-grey covers (monochrome, black-and-white, very bright) get neutral tones instead,
 *  - every stop is darkened until white text stays readable on it.
 */
private fun buildArtworkTheme(path: String): ArtworkTheme? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 128 && bounds.outHeight / (sample * 2) >= 128) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null

    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    val bins = 12
    val binWeight = DoubleArray(bins)
    val binR = DoubleArray(bins)
    val binG = DoubleArray(bins)
    val binB = DoubleArray(bins)
    var sumValue = 0.0
    var sumSaturation = 0.0
    val hsv = FloatArray(3)
    for (px in pixels) {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        sumValue += hsv[2]
        sumSaturation += hsv[1]
        if (hsv[1] < 0.18f || hsv[2] < 0.15f) continue
        val bin = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
        val weight = (hsv[1] * (0.4f + hsv[2])).toDouble()
        binWeight[bin] += weight
        binR[bin] += r * weight
        binG[bin] += g * weight
        binB[bin] += b * weight
    }
    val count = pixels.size.coerceAtLeast(1)
    val avgValue = sumValue / count
    val avgSaturation = sumSaturation / count
    val totalWeight = binWeight.sum()

    val blurred = runCatching { Bitmap.createScaledBitmap(bitmap, 32, 32, true).asImageBitmap() }.getOrNull()

    val stops: List<Color>
    val glow: Color
    if (avgSaturation < 0.10 || totalWeight < count * 0.02) {
        val base = (0.20 + avgValue * 0.25).toFloat().coerceIn(0.22f, 0.42f)
        stops = listOf(grey(base + 0.08f), grey(base), grey(base * 0.45f))
        glow = grey(base + 0.20f)
    } else {
        val dominant = binWeight.indices.maxByOrNull { binWeight[it] } ?: 0
        var secondary = -1
        var secondaryWeight = 0.0
        for (i in 0 until bins) {
            val distance = minOf(abs(i - dominant), bins - abs(i - dominant))
            if (distance >= 2 && binWeight[i] > secondaryWeight) {
                secondary = i
                secondaryWeight = binWeight[i]
            }
        }
        if (secondaryWeight < binWeight[dominant] * 0.25) secondary = -1

        fun bucketColor(bin: Int, value: Float): Color = toned(
            binR[bin] / binWeight[bin],
            binG[bin] / binWeight[bin],
            binB[bin] / binWeight[bin],
            value,
        )
        val accent = if (secondary >= 0) secondary else dominant
        stops = listOf(bucketColor(dominant, 0.50f), bucketColor(accent, 0.36f), bucketColor(dominant, 0.15f))
        glow = bucketColor(accent, 0.62f)
    }
    return ArtworkTheme(stops.map(::ensureReadable), glow, blurred)
}

private fun toned(r: Double, g: Double, b: Double, value: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255), hsv)
    hsv[1] = (hsv[1] * 1.1f).coerceIn(0.38f, 0.85f)
    hsv[2] = value
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun grey(value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(0f, 0f, value.coerceIn(0f, 1f))))

/** White text needs a background luminance of at most ~0.18 to reach a 4.5:1 contrast ratio. */
private fun ensureReadable(color: Color): Color {
    var result = color
    val hsv = FloatArray(3)
    var guard = 0
    while (result.luminance() > 0.17f && guard < 14) {
        android.graphics.Color.colorToHSV(result.toArgb(), hsv)
        hsv[2] *= 0.9f
        result = Color(android.graphics.Color.HSVToColor(hsv))
        guard++
    }
    return result
}
