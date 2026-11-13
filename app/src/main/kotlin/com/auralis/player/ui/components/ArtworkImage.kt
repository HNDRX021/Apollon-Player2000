package com.auralis.player.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Covers are decoded on a small, fixed pool instead of the unbounded IO pool. A fast scroll through a grid
 * asks for dozens of covers at once; on the unbounded pool every one of them started decoding immediately
 * (and could not be cancelled once started, even after its tile had scrolled away), and the many tracks of one
 * album each decoded the same file at the same time. Here the queued requests of tiles that have left the
 * screen are cancelled before they start, and a request that waited finds its cover already in the cache.
 */
private val ArtworkDecodeDispatcher = Dispatchers.IO.limitedParallelism(4)

/**
 * Process-wide cache of decoded covers, bounded by bytes (1/16 of the heap),
 * keyed by file and display size. Covers are decoded at roughly the size they
 * are drawn, so a 10,000-song library never holds full-resolution art.
 */
private object ArtworkCache {
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun key(path: String, targetPx: Int) = "$path@$targetPx"

    fun peek(path: String, targetPx: Int): ImageBitmap? = cache.get(key(path, targetPx))

    /** Blocking decode; call off the main thread. */
    fun load(path: String, targetPx: Int): ImageBitmap? {
        peek(path, targetPx)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return bitmap.asImageBitmap().also { cache.put(key(path, targetPx), it) }
    }
}

/** Decode enough source pixels for the larger rendered dimension, with a bounded memory target. */
internal fun artworkDecodeTargetPx(widthPx: Int, heightPx: Int): Int =
    maxOf(widthPx, heightPx).coerceIn(1, MAX_ARTWORK_DECODE_PX).let { size ->
        if (size == 1) size else ((size + ARTWORK_DECODE_BUCKET_PX - 1) / ARTWORK_DECODE_BUCKET_PX) * ARTWORK_DECODE_BUCKET_PX
    }

private const val MAX_ARTWORK_DECODE_PX = 2048
private const val ARTWORK_DECODE_BUCKET_PX = 64

/**
 * Album cover with a music-note placeholder. [path] is the absolute file path
 * stored in the database; [targetSize] is the size it is drawn at, used to
 * pick a decode size. Size the composable itself through [modifier].
 */
@Composable
fun ArtworkImage(
    path: String?,
    modifier: Modifier = Modifier,
    targetSize: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    val targetPx = with(LocalDensity.current) { targetSize.roundToPx() }
    var measuredSize by remember(targetSize) { mutableIntStateOf(targetPx) }
    val decodeTargetPx = artworkDecodeTargetPx(measuredSize, measuredSize)
    val image by produceState<ImageBitmap?>(
        initialValue = path?.let { ArtworkCache.peek(it, decodeTargetPx) },
        key1 = path,
        key2 = decodeTargetPx,
    ) {
        value = if (path == null) null else withContext(ArtworkDecodeDispatcher) { ArtworkCache.load(path, decodeTargetPx) }
    }

    Box(
        modifier = modifier.onSizeChanged { size: IntSize ->
            val actualTarget = artworkDecodeTargetPx(size.width, size.height)
            if (size.width > 0 && size.height > 0 && measuredSize != actualTarget) measuredSize = actualTarget
        }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = image, animationSpec = tween(220), label = "artwork") { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize(0.5f),
                    )
                }
            }
        }
    }
}
