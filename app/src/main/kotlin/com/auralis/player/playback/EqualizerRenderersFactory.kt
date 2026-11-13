@file:OptIn(UnstableApi::class)

package com.auralis.player.playback

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.auralis.player.audio.EqualizerAudioProcessor
import com.auralis.player.audio.EqualizerParams

/**
 * Builds each ExoPlayer's audio sink with exactly one [EqualizerAudioProcessor]
 * in its processing chain. Used for both the main player and the crossfade
 * shadow player, so the equalizer is applied once per audible stream and the
 * overlap during a crossfade is equalized too.
 */
class EqualizerRenderersFactory(
    context: Context,
    private val params: EqualizerParams,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setAudioProcessors(arrayOf<AudioProcessor>(EqualizerAudioProcessor(params)))
        .build()
}
