package com.auralis.player.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class BiquadEqualizerDspTest {
    private val sampleRate = 48_000

    private fun gains(vararg db: Float) = floatArrayOf(*db)

    private fun sine(frequency: Double, seconds: Double, amplitude: Float): FloatArray =
        FloatArray((seconds * sampleRate).toInt()) { i -> (amplitude * sin(2.0 * PI * frequency * i / sampleRate)).toFloat() }

    private fun rms(samples: FloatArray, from: Int): Double {
        var sum = 0.0
        for (i in from until samples.size) sum += samples[i] * samples[i]
        return sqrt(sum / (samples.size - from))
    }

    private fun processInChunks(dsp: BiquadEqualizerDsp, input: FloatArray, chunk: Int = 1024): FloatArray {
        val out = input.copyOf()
        var pos = 0
        while (pos < out.size) {
            val n = minOf(chunk, out.size - pos)
            val slice = out.copyOfRange(pos, pos + n)
            dsp.process(slice, n)
            slice.copyInto(out, pos)
            pos += n
        }
        return out
    }

    @Test
    fun eachBandBoostsItsOwnFrequencyByTheSetGain() {
        val centres = listOf(60.0, 230.0, 910.0, 3_600.0, 14_000.0)
        for (band in 0 until 5) {
            val g = FloatArray(5).also { it[band] = 6f }
            val atCentre = BiquadEqualizerDsp.responseDb(g, centres[band], sampleRate)
            // Peaking bands reach the full gain at the centre; shelves reach half of it at their corner.
            val expected = if (band == 0 || band == 4) 3.0 else 6.0
            assertEquals("band $band", expected, atCentre, 0.15)
        }
    }

    @Test
    fun peakingBandReachesFullGainAndCutsToo() {
        assertEquals(12.0, BiquadEqualizerDsp.responseDb(gains(0f, 0f, 12f, 0f, 0f), 910.0, sampleRate), 0.05)
        assertEquals(-12.0, BiquadEqualizerDsp.responseDb(gains(0f, 0f, 0f, -12f, 0f), 3_600.0, sampleRate), 0.05)
    }

    @Test
    fun shelvesCoverTheEdgesOfTheSpectrum() {
        assertTrue(BiquadEqualizerDsp.responseDb(gains(12f, 0f, 0f, 0f, 0f), 20.0, sampleRate) > 10.0)
        assertTrue(BiquadEqualizerDsp.responseDb(gains(0f, 0f, 0f, 0f, 12f), 20_000.0, sampleRate) > 9.0)
        // Far from the shelf the signal is left alone.
        assertEquals(0.0, BiquadEqualizerDsp.responseDb(gains(12f, 0f, 0f, 0f, 0f), 5_000.0, sampleRate), 0.5)
    }

    @Test
    fun flatGainsLeaveTheSignalBitIdentical() {
        val dsp = BiquadEqualizerDsp(2, sampleRate)
        dsp.setTarget(true, gains(0f, 0f, 0f, 0f, 0f))
        val input = sine(440.0, 0.2, 0.5f)
        val out = processInChunks(dsp, input)
        assertArrayEquals(input, out, 0f)
    }

    @Test
    fun disabledEqualizerIsABitExactBypassEvenWithGainsSet() {
        val dsp = BiquadEqualizerDsp(1, sampleRate)
        dsp.setTarget(false, gains(12f, 12f, 12f, 12f, 12f))
        val input = sine(910.0, 0.3, 0.5f)
        assertArrayEquals(input, processInChunks(dsp, input), 0f)
    }

    @Test
    fun enabledEqualizerAppliesTheGainToTheActualSignal() {
        val dsp = BiquadEqualizerDsp(1, sampleRate)
        dsp.setTarget(true, gains(0f, 0f, 12f, 0f, 0f))
        val input = sine(910.0, 1.0, 0.1f)
        val out = processInChunks(dsp, input)
        val measured = 20.0 * log10(rms(out, sampleRate / 2) / rms(input, sampleRate / 2))
        assertEquals(12.0, measured, 0.3)
    }

    @Test
    fun turningTheEqualizerOffFadesOutAndEndsBitExact() {
        val dsp = BiquadEqualizerDsp(1, sampleRate)
        dsp.setTarget(true, gains(6f, 6f, 6f, 6f, 6f))
        processInChunks(dsp, sine(910.0, 0.5, 0.1f))

        dsp.setTarget(false, gains(6f, 6f, 6f, 6f, 6f))
        val input = sine(910.0, 0.5, 0.1f)
        val out = processInChunks(dsp, input)
        // After the ~20 ms ramp the tail is untouched.
        val tail = out.size - 4_800
        assertArrayEquals(input.copyOfRange(tail, input.size), out.copyOfRange(tail, out.size), 0f)
        // And the transition itself never jumps: consecutive samples stay close.
        var maxStep = 0f
        for (i in 1 until out.size) maxStep = maxOf(maxStep, abs(out[i] - out[i - 1]))
        assertTrue("max step $maxStep", maxStep < 0.2f)
    }

    @Test
    fun gainChangesGlideInsteadOfJumping() {
        val dsp = BiquadEqualizerDsp(1, sampleRate)
        dsp.setTarget(true, gains(0f, 0f, 0f, 0f, 0f))
        processInChunks(dsp, sine(910.0, 0.2, 0.1f))
        dsp.setTarget(true, gains(0f, 0f, 12f, 0f, 0f))
        val out = processInChunks(dsp, sine(910.0, 0.5, 0.1f))
        var maxStep = 0f
        for (i in 1 until out.size) maxStep = maxOf(maxStep, abs(out[i] - out[i - 1]))
        // A 910 Hz sine at final amplitude 0.4 has a natural per-sample step of ~0.05.
        assertTrue("max step $maxStep", maxStep < 0.2f)
    }

    @Test
    fun softClipIsTransparentBelowTheKneeAndNeverExceedsFullScale() {
        assertEquals(0.5f, BiquadEqualizerDsp.softClip(0.5f), 0f)
        assertEquals(-0.97f, BiquadEqualizerDsp.softClip(-0.97f), 0f)
        for (x in listOf(0.99f, 1.2f, 3f, 50f)) {
            val y = BiquadEqualizerDsp.softClip(x)
            assertTrue(y <= 1f && y >= 0.98f)
            assertEquals(-y, BiquadEqualizerDsp.softClip(-x), 0f)
        }
    }
}
