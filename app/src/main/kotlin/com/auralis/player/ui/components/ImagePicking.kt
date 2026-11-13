package com.auralis.player.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How a picked image is fitted. [ratio] is width / height; null keeps the picture's own shape (no cropping). */
enum class CropAspect(val label: String, val ratio: Float?) {
    Original("Original", null),
    Square("Square 1:1", 1f),
    Portrait("Portrait 4:5", 4f / 5f),
    Landscape("Landscape 16:9", 16f / 9f),
}

class ImagePicker(val pickFromPhotos: () -> Unit, val pickFromFiles: () -> Unit)

/**
 * The two ways to choose a picture. Photos is the system photo picker (the gallery, which is where users
 * keep their pictures) and is what the app opens first; Files is the document browser and is offered as
 * a secondary choice inside the crop screen.
 */
@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): ImagePicker {
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return remember(photos, files) {
        ImagePicker(
            pickFromPhotos = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            pickFromFiles = { files.launch(arrayOf("image/*")) },
        )
    }
}

/**
 * Returns a function that opens the gallery; once a picture is chosen the crop screen appears, and
 * [onCropped] receives the result as high-quality JPEG bytes. Call the returned function from a button.
 */
@Composable
fun rememberCroppedImagePicker(
    title: String,
    initialAspect: CropAspect,
    maxOutputEdge: Int,
    onCropped: (ByteArray) -> Unit,
): () -> Unit {
    var source by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberImagePicker { source = it }
    source?.let { uri ->
        ImageCropDialog(
            source = uri,
            title = title,
            initialAspect = initialAspect,
            maxOutputEdge = maxOutputEdge,
            onDismiss = { source = null },
            onConfirm = { bytes -> source = null; onCropped(bytes) },
            onPickPhotos = picker.pickFromPhotos,
            onPickFiles = picker.pickFromFiles,
        )
    }
    return picker.pickFromPhotos
}

@Composable
fun ImageCropDialog(
    source: Uri,
    title: String,
    initialAspect: CropAspect,
    maxOutputEdge: Int,
    onDismiss: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
    onPickPhotos: () -> Unit,
    onPickFiles: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap by produceState<Bitmap?>(initialValue = null, source) { value = decodeForCrop(context, source) }
    var aspect by remember { mutableStateOf(initialAspect) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf(Size.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassToolbarButton(onClick = onDismiss, enabled = !saving) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                GlassTextButton(
                    enabled = bitmap != null && !saving && frame.width > 0f,
                    onClick = {
                        val picture = bitmap ?: return@GlassTextButton
                        saving = true
                        failed = false
                        scope.launch {
                            val bytes = withContext(Dispatchers.Default) {
                                renderCrop(picture, frame, zoom, offset, maxOutputEdge)
                            }
                            saving = false
                            if (bytes == null) failed = true else onConfirm(bytes)
                        }
                    },
                ) { Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bmp = bitmap
                if (bmp == null) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    val viewW = constraints.maxWidth.toFloat()
                    val viewH = constraints.maxHeight.toFloat()
                    val ratio = aspect.ratio ?: (bmp.width.toFloat() / bmp.height)
                    val margin = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
                    val availW = max(1f, viewW - margin)
                    val availH = max(1f, viewH - margin)
                    val frameW = min(availW, availH * ratio)
                    val frameH = frameW / ratio
                    frame = Size(frameW, frameH)
                    val baseScale = max(frameW / bmp.width, frameH / bmp.height)
                    val displayScale = baseScale * zoom

                    fun clamp(o: Offset, scale: Float): Offset {
                        val maxX = max(0f, (bmp.width * scale - frameW) / 2f)
                        val maxY = max(0f, (bmp.height * scale - frameH) / 2f)
                        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                    }

                    Canvas(
                        Modifier.fillMaxSize().pointerInput(aspect, bmp) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                if (aspect.ratio == null) return@detectTransformGestures
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, 8f)
                                zoom = newZoom
                                offset = clamp(offset + pan, baseScale * newZoom)
                            }
                        },
                    ) {
                        val dw = bmp.width * displayScale
                        val dh = bmp.height * displayScale
                        val clamped = clamp(offset, displayScale)
                        val left = size.width / 2f - dw / 2f + clamped.x
                        val top = size.height / 2f - dh / 2f + clamped.y
                        drawImage(
                            image = image,
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                            filterQuality = FilterQuality.High,
                        )
                        val fl = size.width / 2f - frameW / 2f
                        val ft = size.height / 2f - frameH / 2f
                        clipRect(fl, ft, fl + frameW, ft + frameH, ClipOp.Difference) {
                            drawRect(Color.Black.copy(alpha = 0.62f))
                        }
                        drawRect(Color.White.copy(alpha = 0.9f), Offset(fl, ft), Size(frameW, frameH), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }

            if (failed) {
                Text(
                    "That picture couldn't be processed. Try another one.",
                    color = Color(0xFFFF8A80),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            Text(
                if (aspect.ratio == null) "Original keeps the whole picture." else "Drag to move, pinch to zoom.",
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CropAspect.entries.forEach { option ->
                    GlassChip(
                        label = option.label,
                        selected = aspect == option,
                        onClick = { if (!saving) { aspect = option; zoom = 1f; offset = Offset.Zero } },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                GlassTextButton(onClick = onPickPhotos, enabled = !saving) { Text("Choose another photo", color = Color.White) }
                GlassTextButton(onClick = onPickFiles, enabled = !saving) { Text("Choose from Files", color = Color.White) }
            }
        }
    }
}

/** Decodes [uri] (longest side capped near 3000 px, EXIF rotation applied) so it can be shown and cropped. */
private suspend fun decodeForCrop(context: Context, uri: Uri, maxEdge: Int = 3072): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext null
        val orientation = resolver.openInputStream(uri)?.use {
            runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return@withContext decoded
        }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    } catch (e: Exception) {
        null
    }
}

/**
 * Cuts the region inside the on-screen frame out of [source] at the picture's own resolution (nothing is
 * stretched; the frame has the chosen aspect ratio), scales down only if it exceeds [maxEdge], and encodes
 * a high-quality JPEG.
 */
private fun renderCrop(source: Bitmap, frame: Size, zoom: Float, offset: Offset, maxEdge: Int): ByteArray? {
    return try {
        val baseScale = max(frame.width / source.width, frame.height / source.height)
        val scale = baseScale * zoom
        val maxX = max(0f, (source.width * scale - frame.width) / 2f)
        val maxY = max(0f, (source.height * scale - frame.height) / 2f)
        val ox = offset.x.coerceIn(-maxX, maxX)
        val oy = offset.y.coerceIn(-maxY, maxY)
        val srcW = min(source.width.toFloat(), frame.width / scale)
        val srcH = min(source.height.toFloat(), frame.height / scale)
        val left = (source.width / 2f - ox / scale - srcW / 2f).coerceIn(0f, source.width - srcW)
        val top = (source.height / 2f - oy / scale - srcH / 2f).coerceIn(0f, source.height - srcH)
        var cropped = Bitmap.createBitmap(
            source,
            left.roundToInt(),
            top.roundToInt(),
            min(srcW.roundToInt(), source.width - left.roundToInt()).coerceAtLeast(1),
            min(srcH.roundToInt(), source.height - top.roundToInt()).coerceAtLeast(1),
        )
        val longest = max(cropped.width, cropped.height)
        if (longest > maxEdge) {
            val f = maxEdge.toFloat() / longest
            cropped = Bitmap.createScaledBitmap(cropped, (cropped.width * f).roundToInt().coerceAtLeast(1), (cropped.height * f).roundToInt().coerceAtLeast(1), true)
        }
        // JPEG has no transparency: flatten onto white so a PNG's transparent areas don't turn black.
        val flat = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(flat)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(cropped, 0f, 0f, null)
        val out = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, 95, out)
        out.toByteArray()
    } catch (e: Exception) {
        null
    }
}
