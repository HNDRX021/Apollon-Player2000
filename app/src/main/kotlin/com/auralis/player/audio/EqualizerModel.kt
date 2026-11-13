package com.auralis.player.audio

import com.auralis.player.settings.SavedEqPreset

/**
 * Pure (Android-free) description of the five-band equalizer: the bands, the
 * built-in presets and the rules for choosing which preset counts as
 * "selected" and for naming custom presets. Gains are stored in millibels
 * (1 dB = 100 mB), the same unit the persisted settings already use.
 */
object EqualizerModel {
    const val BAND_COUNT = 5
    const val MIN_GAIN_DB = -12
    const val MAX_GAIN_DB = 12
    const val MIN_GAIN_MB = MIN_GAIN_DB * 100
    const val MAX_GAIN_MB = MAX_GAIN_DB * 100
    const val MAX_PRESET_NAME_LENGTH = 40
    const val MAX_CUSTOM_PRESETS = 30

    /** Centre (or shelf corner) frequency of each band, low to high. */
    val BAND_FREQUENCIES_HZ: List<Int> = listOf(60, 230, 910, 3_600, 14_000)

    /** Short axis labels, "Hz" implied below 1 kHz exactly like the reference design. */
    val BAND_LABELS: List<String> = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    const val FLAT_ID = "flat"
    private const val CUSTOM_PREFIX = "custom:"

    class BuiltInPreset(val id: String, val englishName: String, val gainsDb: List<Int>) {
        val levelsMb: List<Int> = gainsDb.map { it * 100 }
    }

    /** Display order: Flat first, then the seven genre presets in the specified order. */
    val BUILT_IN_PRESETS: List<BuiltInPreset> = listOf(
        BuiltInPreset(FLAT_ID, "Flat", listOf(0, 0, 0, 0, 0)),
        BuiltInPreset("rock", "Rock", listOf(3, 2, -1, 2, 2)),
        BuiltInPreset("metal", "Metal", listOf(3, 1, -2, 3, 2)),
        BuiltInPreset("hip_hop", "Hip-Hop", listOf(4, 2, 0, 1, 2)),
        BuiltInPreset("pop", "Pop", listOf(2, 1, 0, 2, 2)),
        BuiltInPreset("country", "Country", listOf(2, 1, 0, 2, 1)),
        BuiltInPreset("electronic", "Electronic", listOf(4, 2, 0, 2, 3)),
        BuiltInPreset("ambient", "Ambient", listOf(-1, 0, 1, 1, 2)),
    )

    val FLAT_LEVELS_MB: List<Int> = List(BAND_COUNT) { 0 }

    fun customId(name: String): String = CUSTOM_PREFIX + name
    fun isCustomId(id: String): Boolean = id.startsWith(CUSTOM_PREFIX)
    fun customNameOf(id: String): String? = id.takeIf(::isCustomId)?.removePrefix(CUSTOM_PREFIX)
    fun builtInById(id: String): BuiltInPreset? = BUILT_IN_PRESETS.firstOrNull { it.id == id }

    fun clampMb(levelMb: Int): Int = levelMb.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)

    /** Always exactly [BAND_COUNT] clamped values; anything malformed becomes flat. */
    fun sanitize(levelsMb: List<Int>): List<Int> =
        if (levelsMb.size == BAND_COUNT) levelsMb.map(::clampMb) else FLAT_LEVELS_MB

    /** Custom presets that do not have exactly five bands (legacy device presets) are dropped. */
    fun sanitizeCustom(presets: List<SavedEqPreset>): List<SavedEqPreset> =
        presets.filter { it.bandLevelsMb.size == BAND_COUNT }
            .map { it.copy(bandLevelsMb = it.bandLevelsMb.map(::clampMb)) }

    private fun levelsOf(id: String, custom: List<SavedEqPreset>): List<Int>? {
        builtInById(id)?.let { return it.levelsMb }
        val name = customNameOf(id) ?: return null
        return custom.firstOrNull { it.name == name }?.bandLevelsMb
    }

    /**
     * Which single preset (if any) matches [levelsMb]. The previously selected
     * preset wins while it still matches, so two identical presets do not make
     * the highlight jump; otherwise the first match in display order, or null
     * when the sliders describe a custom curve that is not saved.
     */
    fun resolveSelection(levelsMb: List<Int>, preferredId: String?, custom: List<SavedEqPreset>): String? {
        if (preferredId != null && levelsOf(preferredId, custom) == levelsMb) return preferredId
        BUILT_IN_PRESETS.firstOrNull { it.levelsMb == levelsMb }?.let { return it.id }
        custom.firstOrNull { it.bandLevelsMb == levelsMb }?.let { return customId(it.name) }
        return null
    }

    sealed interface SaveResult {
        data object Saved : SaveResult
        data object EmptyName : SaveResult
        data object DuplicateName : SaveResult
        data object ReservedName : SaveResult
        data object LimitReached : SaveResult
    }

    /**
     * Applies the "create custom preset" rules to [existing]. A name that is
     * empty, reserved by a built-in preset or (unless [replaceExisting]) already
     * used is rejected and [existing] is returned unchanged, so a saved preset
     * is only ever overwritten when the caller explicitly asks for it.
     */
    fun savePreset(
        existing: List<SavedEqPreset>,
        name: String,
        levelsMb: List<Int>,
        replaceExisting: Boolean,
    ): Pair<SaveResult, List<SavedEqPreset>> {
        val trimmed = name.trim().take(MAX_PRESET_NAME_LENGTH)
        if (trimmed.isEmpty()) return SaveResult.EmptyName to existing
        if (BUILT_IN_PRESETS.any { it.englishName.equals(trimmed, ignoreCase = true) }) {
            return SaveResult.ReservedName to existing
        }
        val clash = existing.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        if (clash != null && !replaceExisting) return SaveResult.DuplicateName to existing
        if (clash == null && existing.size >= MAX_CUSTOM_PRESETS) return SaveResult.LimitReached to existing
        val entry = SavedEqPreset(clash?.name ?: trimmed, sanitize(levelsMb))
        val updated = if (clash != null) existing.map { if (it === clash) entry else it } else existing + entry
        return SaveResult.Saved to updated
    }

    /** "+3 dB", "0 dB", "-12 dB" — the exact value the filter is running with. */
    fun formatGain(levelMb: Int): String {
        val db = levelMb / 100.0
        val text = if (levelMb % 100 == 0) "${levelMb / 100}" else "%.1f".format(java.util.Locale.US, db)
        return if (levelMb > 0) "+$text dB" else "$text dB"
    }
}
