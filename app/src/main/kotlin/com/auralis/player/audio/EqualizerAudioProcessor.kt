@file:OptIn(UnstableApi::class)

package com.auralis.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Applies the five-band EQ inside ExoPlayer's own audio pipeline, on the PCM
 * that is about to be written to the AudioTrack. Because it is part of the
 * player's sink it can never be attached twice, never lose its connection
 * when the audio session changes, and behaves the same on every device
 * (the platform android.media.audiofx.Equalizer depends on the device's
 * band layout and on OEM effect support).
 *
 * Each ExoPlayer (the main one and the crossfade shadow) creates its own
 * instance; all of them follow the same [EqualizerParams].
 *
 * Only 16-bit and float PCM are processed. Any other format is left alone
 * (the processor reports itself inactive), so playback never fails because
 * of the EQ.
 */
class EqualizerAudioProcessor(private val params: EqualizerParams) : BaseAudioProcessor() {

    private var dsp: BiquadEqualizerDsp? = null
    private var scratch = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val encoding = inputAudioFormat.encoding
        val supported = encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT
        if (!supported || inputAudioFormat.channelCount !in 1..8 || inputAudioFormat.sampleRate <= 0) {
            dsp = null
            return AudioFormat.NOT_SET
        }
        dsp = BiquadEqualizerDsp(inputAudioFormat.channelCount, inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val engine = dsp
        val format = inputAudioFormat
        val remaining = inputBuffer.remaining()
        if (engine == null || remaining == 0) {
            if (remaining > 0) replaceOutputBuffer(remaining).put(inputBuffer).flip()
            return
        }

        val snapshot = params.current()
        engine.setTarget(snapshot.enabled, snapshot.gainsDb)

        val output = replaceOutputBuffer(remaining)
        if (engine.isPassThrough()) {
            // Bit-exact copy; still lets the DSP settle its state/gain targets.
            output.put(inputBuffer)
            output.flip()
            engine.process(scratch, 0)
            return
        }

        val bytesPerSample = if (format.encoding == C.ENCODING_PCM_16BIT) 2 else 4
        val samples = remaining / bytesPerSample
        val frames = samples / format.channelCount
        if (scratch.size < samples) scratch = FloatArray(samples)

        if (format.encoding == C.ENCODING_PCM_16BIT) {
            for (i in 0 until samples) scratch[i] = inputBuffer.getShort() / 32768f
        } else {
            for (i in 0 until samples) scratch[i] = inputBuffer.getFloat()
        }
        engine.process(scratch, frames)
        if (format.encoding == C.ENCODING_PCM_16BIT) {
            for (i in 0 until samples) {
                val v = (scratch[i] * 32768f).toInt().coerceIn(-32768, 32767)
                output.putShort(v.toShort())
            }
        } else {
            for (i in 0 until samples) output.putFloat(scratch[i])
        }
        output.flip()
    }

    override fun onReset() {
        dsp = null
        scratch = FloatArray(0)
    }
}
