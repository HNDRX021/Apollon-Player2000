package com.auralis.player.audio

import com.auralis.player.settings.SavedEqPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerModelTest {

    @Test
    fun usesExactlyTheFiveSpecifiedBands() {
        assertEquals(listOf(60, 230, 910, 3_600, 14_000), EqualizerModel.BAND_FREQUENCIES_HZ)
        assertEquals(listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz"), EqualizerModel.BAND_LABELS)
        assertEquals(5, EqualizerModel.BAND_COUNT)
        assertEquals(-1200, EqualizerModel.MIN_GAIN_MB)
        assertEquals(1200, EqualizerModel.MAX_GAIN_MB)
    }

    @Test
    fun builtInPresetsHaveTheSpecifiedValuesInOrder() {
        val expected = listOf(
            "flat" to listOf(0, 0, 0, 0, 0),
            "rock" to listOf(3, 2, -1, 2, 2),
            "metal" to listOf(3, 1, -2, 3, 2),
            "hip_hop" to listOf(4, 2, 0, 1, 2),
            "pop" to listOf(2, 1, 0, 2, 2),
            "country" to listOf(2, 1, 0, 2, 1),
            "electronic" to listOf(4, 2, 0, 2, 3),
            "ambient" to listOf(-1, 0, 1, 1, 2),
        )
        assertEquals(expected, EqualizerModel.BUILT_IN_PRESETS.map { it.id to it.gainsDb })
        EqualizerModel.BUILT_IN_PRESETS.forEach { preset ->
            assertEquals(preset.gainsDb.map { it * 100 }, preset.levelsMb)
            assertTrue(preset.levelsMb.all { it in EqualizerModel.MIN_GAIN_MB..EqualizerModel.MAX_GAIN_MB })
        }
    }

    @Test
    fun selectionFollowsTheSlidersAndNeverHasTwoPresets() {
        val rock = EqualizerModel.builtInById("rock")!!.levelsMb
        assertEquals("rock", EqualizerModel.resolveSelection(rock, null, emptyList()))
        assertEquals("flat", EqualizerModel.resolveSelection(EqualizerModel.FLAT_LEVELS_MB, "rock", emptyList()))

        val edited = rock.toMutableList().also { it[0] = it[0] + 100 }
        assertNull(EqualizerModel.resolveSelection(edited, "rock", emptyList()))

        val custom = listOf(SavedEqPreset("Mine", edited))
        assertEquals("custom:Mine", EqualizerModel.resolveSelection(edited, null, custom))
    }

    @Test
    fun preferredPresetWinsWhenTwoPresetsAreIdentical() {
        val rock = EqualizerModel.builtInById("rock")!!.levelsMb
        val custom = listOf(SavedEqPreset("My rock", rock))
        assertEquals("custom:My rock", EqualizerModel.resolveSelection(rock, "custom:My rock", custom))
        assertEquals("rock", EqualizerModel.resolveSelection(rock, null, custom))
    }

    @Test
    fun savingRejectsEmptyReservedAndDuplicateNames() {
        val levels = listOf(100, 200, 0, -100, 300)
        val (empty, _) = EqualizerModel.savePreset(emptyList(), "   ", levels, replaceExisting = false)
        assertEquals(EqualizerModel.SaveResult.EmptyName, empty)

        val (reserved, _) = EqualizerModel.savePreset(emptyList(), "ROCK", levels, replaceExisting = false)
        assertEquals(EqualizerModel.SaveResult.ReservedName, reserved)

        val (saved, list) = EqualizerModel.savePreset(emptyList(), "  Night ", levels, replaceExisting = false)
        assertEquals(EqualizerModel.SaveResult.Saved, saved)
        assertEquals(listOf(SavedEqPreset("Night", levels)), list)

        val other = listOf(0, 0, 0, 0, 0)
        val (duplicate, unchanged) = EqualizerModel.savePreset(list, "night", other, replaceExisting = false)
        assertEquals(EqualizerModel.SaveResult.DuplicateName, duplicate)
        assertEquals(list, unchanged)

        val (replaced, updated) = EqualizerModel.savePreset(list, "night", other, replaceExisting = true)
        assertEquals(EqualizerModel.SaveResult.Saved, replaced)
        assertEquals(listOf(SavedEqPreset("Night", other)), updated)
    }

    @Test
    fun savingStopsAtTheLimit() {
        val full = List(EqualizerModel.MAX_CUSTOM_PRESETS) { SavedEqPreset("P$it", EqualizerModel.FLAT_LEVELS_MB) }
        val (result, list) = EqualizerModel.savePreset(full, "One more", EqualizerModel.FLAT_LEVELS_MB, replaceExisting = false)
        assertEquals(EqualizerModel.SaveResult.LimitReached, result)
        assertEquals(full, list)
    }

    @Test
    fun malformedLevelsBecomeFlatAndValuesAreClamped() {
        assertEquals(EqualizerModel.FLAT_LEVELS_MB, EqualizerModel.sanitize(listOf(1, 2, 3)))
        assertEquals(listOf(-1200, 1200, 0, 100, -100), EqualizerModel.sanitize(listOf(-5000, 5000, 0, 100, -100)))
        val kept = SavedEqPreset("ok", listOf(0, 0, 0, 0, 0))
        val legacy = SavedEqPreset("legacy ten band", List(10) { 0 })
        assertEquals(listOf(kept), EqualizerModel.sanitizeCustom(listOf(kept, legacy)))
    }

    @Test
    fun gainTextShowsTheExactValue() {
        assertEquals("+3 dB", EqualizerModel.formatGain(300))
        assertEquals("0 dB", EqualizerModel.formatGain(0))
        assertEquals("-12 dB", EqualizerModel.formatGain(-1200))
        assertEquals("+12 dB", EqualizerModel.formatGain(1200))
    }
}
