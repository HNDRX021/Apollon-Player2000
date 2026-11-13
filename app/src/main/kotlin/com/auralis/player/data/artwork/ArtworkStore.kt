package com.auralis.player.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps album artwork as downscaled JPEG files in app-private storage.
 *
 * Files are named after a hash of the source bytes, so an album whose tracks
 * all embed the same cover, or two albums sharing one, cost a single file.
 * Only the file path goes in the database; pixels are decoded on demand (and
 * cached at display size) by the UI layer, never held wholesale in memory.
 */
@Singleton
class ArtworkStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** @return the absolute path of the stored JPEG, or null if [bytes] isn't a decodable image. */
    fun save(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val dir = File(context.filesDir, DIR_NAME).also { it.mkdirs() }
        val target = File(dir, sha1(bytes) + ".jpg")
        if (target.exists()) return target.absolutePath

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var path: String? = null
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= MAX_EDGE_PX && bounds.outHeight / (sample * 2) >= MAX_EDGE_PX) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (bitmap != null) {
                    // Write to a temp file first so a crash mid-write can't leave a truncated cover behind.
                    val temp = File(dir, target.name + ".tmp")
                    temp.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
                    bitmap.recycle()
                    if (temp.renameTo(target)) {
                        path = target.absolutePath
                    } else {
                        temp.delete()
                    }
                }
            }
            path
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Turns any decodable picture (JPEG, PNG, WEBP, ...) into a JPEG of at most [maxEdge] pixels per side,
     * flattened onto white, ready to be embedded in an audio file. Null when [bytes] is not an image.
     */
    fun toEmbeddableJpeg(bytes: ByteArray, maxEdge: Int = 1400): ByteArray? {
        if (bytes.isEmpty()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxEdge && bounds.outHeight / (sample * 2) >= maxEdge) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val flat = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(flat)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(decoded, 0f, 0f, null)
            decoded.recycle()
            val out = java.io.ByteArrayOutputStream()
            flat.compress(Bitmap.CompressFormat.JPEG, 92, out)
            flat.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIR_NAME = "artwork"
        const val MAX_EDGE_PX = 1024
        const val JPEG_QUALITY = 88
    }
}
