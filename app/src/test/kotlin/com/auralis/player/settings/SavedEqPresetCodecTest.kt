package com.auralis.player.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedEqPresetCodecTest {
    @Test
    fun roundTripsUnicodeNamesAndBandLevels() {
        val presets = listOf(SavedEqPreset("اتاق|محبوب", listOf(-600, 0, 450)))

        assertEquals(presets, SavedEqPresetCodec.decode(SavedEqPresetCodec.encode(presets)))
    }

    @Test
    fun ignoresCorruptRowsInsteadOfDroppingValidPresets() {
        val valid = SavedEqPreset("Warm", listOf(-200, 100))
        val encoded = SavedEqPresetCodec.encode(listOf(valid)) + "|broken-row"

        assertEquals(listOf(valid), SavedEqPresetCodec.decode(encoded))
    }
}
