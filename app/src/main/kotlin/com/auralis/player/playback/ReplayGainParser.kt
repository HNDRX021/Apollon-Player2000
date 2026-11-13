package com.auralis.player.playback

import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment

/** Reads the standard ReplayGain tag variants exposed by Media3 while a track is playing. */
@OptIn(UnstableApi::class)
internal object ReplayGainParser {
    fun trackGainDb(metadata: Metadata): Float? {
        val tags = buildList {
            for (index in 0 until metadata.length()) {
                val entry = metadata[index]
                when (entry) {
                    is VorbisComment -> add(entry.key to entry.value)
                    is CommentFrame -> add(entry.description to entry.text)
                    is TextInformationFrame -> {
                        val description = entry.description
                        if (!description.isNullOrBlank()) add(description to entry.value)
                    }
                }
            }
        }
        return tags.firstNotNullOfOrNull { (name, value) ->
            if (name.equals("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) parseDb(value) else null
        } ?: tags.firstNotNullOfOrNull { (name, value) ->
            if (name.equals("REPLAYGAIN_ALBUM_GAIN", ignoreCase = true)) parseDb(value) else null
        }
    }

    internal fun parseDb(value: String): Float? = value
        .trim()
        .removeSuffix("dB")
        .trim()
        .toFloatOrNull()
        ?.takeIf(Float::isFinite)
        ?.coerceIn(-24f, 12f)
}
