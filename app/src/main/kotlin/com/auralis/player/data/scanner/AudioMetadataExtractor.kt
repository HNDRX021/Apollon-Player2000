package com.auralis.player.data.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import com.auralis.player.domain.util.normalizeReleaseDate
import javax.inject.Inject

/** Raw tag data pulled from a single audio file, before any DB lookups. */
data class ExtractedMetadata(
    val title: String,
    val artistName: String?,
    val albumTitle: String?,
    val albumArtistName: String?,
    val genreName: String?,
    val composer: String?,
    val year: Int?,
    val trackNumber: Int?,
    val totalTracks: Int?,
    val discNumber: Int,
    val totalDiscs: Int?,
    val durationMs: Long,
    val bitrateKbps: Int?,
    val sampleRateHz: Int? = null,
    /** Raw embedded cover image, if the file has one. Saved by the scanner only when the album still lacks artwork. */
    val artworkBytes: ByteArray? = null,
    /** Normalised release date (`yyyy`, `yyyy-MM`, `yyyy-MM-dd`) from the date tag, if any. */
    val releaseDate: String? = null,
)

class AudioMetadataExtractor @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * @param fallbackTitle used when the file has no embedded title tag —
     *   the display name, minus its extension.
     */
    fun extract(treeUri: Uri, documentId: String, fallbackTitle: String): ExtractedMetadata? {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, documentUri)

            val (trackNumber, totalTracks) = retriever.splitNumberSlashTotal(
                MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,
            )
            val (discNumber, totalDiscs) = retriever.splitNumberSlashTotal(
                MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER,
            )

            val releaseDate = normalizeReleaseDate(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))

            ExtractedMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.trim()?.ifBlank { null } ?: fallbackTitle,
                artistName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).cleanTag(),
                albumTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).cleanTag(),
                albumArtistName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).cleanTag(),
                genreName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).cleanTag(),
                composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER).cleanTag(),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.trim()?.take(4)?.toIntOrNull()
                    ?: releaseDate?.take(4)?.toIntOrNull(),
                trackNumber = trackNumber,
                totalTracks = totalTracks,
                discNumber = discNumber ?: 1,
                totalDiscs = totalDiscs,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                bitrateKbps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()?.let { it / 1000 },
                // METADATA_KEY_SAMPLERATE exists from API 31; older devices simply report nothing.
                sampleRateHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
                } else {
                    null
                },
                artworkBytes = retriever.embeddedPicture,
                releaseDate = releaseDate,
            )
        } catch (e: Exception) {
            // Corrupt file, unsupported codec, or a transient SAF access
            // error. The caller skips this document for the current scan
            // pass rather than crashing the whole scan.
            null
        } finally {
            retriever.release()
        }
    }

    /** Trims a tag and treats blank values as missing, so "" never becomes an artist/album/genre row. */
    private fun String?.cleanTag(): String? = this?.trim()?.ifBlank { null }

    /**
     * Tag values for track/disc numbers commonly arrive as "3" or "3/12".
     * Returns (number, total) — either half may be null.
     */
    private fun MediaMetadataRetriever.splitNumberSlashTotal(key: Int): Pair<Int?, Int?> {
        val raw = extractMetadata(key)?.trim() ?: return null to null
        val parts = raw.split("/")
        val number = parts.getOrNull(0)?.toIntOrNull()
        val total = parts.getOrNull(1)?.toIntOrNull()
        return number to total
    }
}
