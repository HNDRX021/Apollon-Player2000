package com.auralis.player.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Gains the playback pipeline should be running with. A single instance is
 * shared by every player (main + crossfade shadow), each of which owns its own
 * filter state; this object is only the volatile "what to apply" snapshot.
 */
@Singleton
class EqualizerParams @Inject constructor() {
    @Volatile private var snapshot = Snapshot(enabled = false, gainsDb = FloatArray(EqualizerModel.BAND_COUNT))

    class Snapshot(val enabled: Boolean, val gainsDb: FloatArray)

    fun current(): Snapshot = snapshot

    fun update(enabled: Boolean, levelsMb: List<Int>) {
        val gains = FloatArray(EqualizerModel.BAND_COUNT) { band ->
            (levelsMb.getOrNull(band) ?: 0).coerceIn(EqualizerModel.MIN_GAIN_MB, EqualizerModel.MAX_GAIN_MB) / 100f
        }
        snapshot = Snapshot(enabled, gains)
    }
}

/**
 * Five cascaded biquads per channel (RBJ cookbook): low shelf at 60 Hz,
 * peaking at 230 Hz / 910 Hz / 3.6 kHz (Q 0.7, about two octaves wide so
 * neighbouring bands overlap smoothly) and high shelf at 14 kHz.
 *
 * Click protection: gain changes glide a fraction of a dB per tiny block
 * instead of jumping, and enabling/disabling crossfades dry and filtered
 * signal over ~20 ms. When the EQ is off, or every gain is 0 dB, the audio
 * is passed through untouched and no filter work is done.
 *
 * Not thread-safe: one instance per audio processing thread.
 */
class BiquadEqualizerDsp(private val channelCount: Int, private val sampleRate: Int) {
    private val bandCount = EqualizerModel.BAND_COUNT
    private val currentGainsDb = FloatArray(bandCount)
    private val targetGainsDb = FloatArray(bandCount)
    private var targetEnabled = false
    private var mix = 0f

    // coefficients[band] = b0, b1, b2, a1, a2 (already divided by a0)
    private val coefficients = Array(bandCount) { DoubleArray(5) }
    // Transposed direct form II state: [channel][band][2]
    private val state = Array(channelCount) { Array(bandCount) { DoubleArray(2) } }

    private val mixStep = 1f / (RAMP_SECONDS * sampleRate).coerceAtLeast(1f)
    private var framesUntilCoefficientUpdate = 0

    init {
        for (band in 0 until bandCount) writeCoefficients(band, 0f)
    }

    fun setTarget(enabled: Boolean, gainsDb: FloatArray) {
        targetEnabled = enabled
        for (band in 0 until bandCount) targetGainsDb[band] = gainsDb.getOrElse(band) { 0f }
    }

    /** True while the output is bit-identical to the input, so callers can skip conversion work. */
    fun isPassThrough(): Boolean = (!targetEnabled && mix == 0f) || (isFlat(currentGainsDb) && isFlat(targetGainsDb))

    /** Processes [frames] interleaved frames of float samples (nominal range -1..1) in place. */
    fun process(samples: FloatArray, frames: Int) {
        if (isPassThrough()) {
            snapToTarget()
            clearState()
            return
        }
        val targetMix = if (targetEnabled) 1f else 0f
        for (frame in 0 until frames) {
            if (framesUntilCoefficientUpdate == 0) {
                glideGains()
                framesUntilCoefficientUpdate = BLOCK_FRAMES
            }
            framesUntilCoefficientUpdate--
            mix = when {
                mix < targetMix -> minOf(targetMix, mix + mixStep)
                mix > targetMix -> maxOf(targetMix, mix - mixStep)
                else -> mix
            }
            val base = frame * channelCount
            for (channel in 0 until channelCount) {
                val dry = samples[base + channel]
                var x = dry.toDouble()
                val channelState = state[channel]
                for (band in 0 until bandCount) {
                    val c = coefficients[band]
                    val s = channelState[band]
                    val y = c[0] * x + s[0]
                    s[0] = c[1] * x - c[3] * y + s[1]
                    s[1] = c[2] * x - c[4] * y
                    x = y
                }
                samples[base + channel] = softClip(dry + (x.toFloat() - dry) * mix)
            }
        }
    }

    private fun glideGains() {
        var changed = false
        for (band in 0 until bandCount) {
            val delta = targetGainsDb[band] - currentGainsDb[band]
            if (delta != 0f) {
                // Land exactly on the target (no float residue), so 0 dB really is flat.
                currentGainsDb[band] = if (abs(delta) <= GAIN_STEP_DB) targetGainsDb[band]
                else currentGainsDb[band] + if (delta > 0f) GAIN_STEP_DB else -GAIN_STEP_DB
                changed = true
            }
        }
        if (changed) for (band in 0 until bandCount) writeCoefficients(band, currentGainsDb[band])
    }

    private fun snapToTarget() {
        for (band in 0 until bandCount) currentGainsDb[band] = targetGainsDb[band]
        for (band in 0 until bandCount) writeCoefficients(band, currentGainsDb[band])
        mix = if (targetEnabled) 1f else 0f
        framesUntilCoefficientUpdate = 0
    }

    private fun clearState() {
        for (channel in state) for (band in channel) { band[0] = 0.0; band[1] = 0.0 }
    }

    private fun isFlat(gains: FloatArray) = gains.all { it == 0f }

    private fun writeCoefficients(band: Int, gainDb: Float) {
        val out = coefficients[band]
        if (gainDb == 0f) {
            out[0] = 1.0; out[1] = 0.0; out[2] = 0.0; out[3] = 0.0; out[4] = 0.0
            return
        }
        val nyquistLimit = sampleRate * 0.45
        val frequency = EqualizerModel.BAND_FREQUENCIES_HZ[band].toDouble().coerceAtMost(nyquistLimit)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * frequency / sampleRate
        val cosW = cos(w0)
        val sinW = sin(w0)
        val b0: Double; val b1: Double; val b2: Double; val a0: Double; val a1: Double; val a2: Double
        when (band) {
            0 -> { // low shelf, slope 1
                val beta = 2.0 * sqrt(a) * (sinW / sqrt(2.0))
                b0 = a * ((a + 1) - (a - 1) * cosW + beta)
                b1 = 2 * a * ((a - 1) - (a + 1) * cosW)
                b2 = a * ((a + 1) - (a - 1) * cosW - beta)
                a0 = (a + 1) + (a - 1) * cosW + beta
                a1 = -2 * ((a - 1) + (a + 1) * cosW)
                a2 = (a + 1) + (a - 1) * cosW - beta
            }
            bandCount - 1 -> { // high shelf, slope 1
                val beta = 2.0 * sqrt(a) * (sinW / sqrt(2.0))
                b0 = a * ((a + 1) + (a - 1) * cosW + beta)
                b1 = -2 * a * ((a - 1) + (a + 1) * cosW)
                b2 = a * ((a + 1) + (a - 1) * cosW - beta)
                a0 = (a + 1) - (a - 1) * cosW + beta
                a1 = 2 * ((a - 1) - (a + 1) * cosW)
                a2 = (a + 1) - (a - 1) * cosW - beta
            }
            else -> { // peaking
                val alpha = sinW / (2.0 * PEAK_Q)
                b0 = 1 + alpha * a
                b1 = -2 * cosW
                b2 = 1 - alpha * a
                a0 = 1 + alpha / a
                a1 = -2 * cosW
                a2 = 1 - alpha / a
            }
        }
        out[0] = b0 / a0; out[1] = b1 / a0; out[2] = b2 / a0; out[3] = a1 / a0; out[4] = a2 / a0
    }

    companion object {
        const val PEAK_Q = 0.7
        private const val BLOCK_FRAMES = 32
        private const val GAIN_STEP_DB = 0.5f
        private const val RAMP_SECONDS = 0.02f
        private const val KNEE = 0.98f

        /** Transparent below 0.98 full scale, smoothly saturating above instead of hard clipping. */
        fun softClip(x: Float): Float {
            val magnitude = abs(x)
            if (magnitude <= KNEE) return x
            val shaped = KNEE + (1f - KNEE) * tanh((magnitude - KNEE) / (1f - KNEE))
            return if (x < 0f) -shaped else shaped
        }

        /** Magnitude response in dB at [frequencyHz] for the given per-band gains (for tests and diagnostics). */
        fun responseDb(gainsDb: FloatArray, frequencyHz: Double, sampleRate: Int): Double {
            val dsp = BiquadEqualizerDsp(1, sampleRate)
            var total = 0.0
            val w = 2.0 * PI * frequencyHz / sampleRate
            for (band in 0 until EqualizerModel.BAND_COUNT) {
                dsp.writeCoefficients(band, gainsDb.getOrElse(band) { 0f })
                val c = dsp.coefficients[band]
                // |H(e^jw)|^2 with H = (b0 + b1 z^-1 + b2 z^-2) / (1 + a1 z^-1 + a2 z^-2)
                val cos1 = cos(w); val sin1 = sin(w); val cos2 = cos(2 * w); val sin2 = sin(2 * w)
                val numRe = c[0] + c[1] * cos1 + c[2] * cos2
                val numIm = -(c[1] * sin1 + c[2] * sin2)
                val denRe = 1 + c[3] * cos1 + c[4] * cos2
                val denIm = -(c[3] * sin1 + c[4] * sin2)
                val magSq = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm)
                total += 10.0 * kotlin.math.log10(magSq)
            }
            return total
        }
    }
}
