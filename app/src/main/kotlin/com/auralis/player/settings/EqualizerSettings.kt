package com.auralis.player.settings

/**
 * Persisted equalizer state. [bandLevels] are in millibels, one per band
 * (exactly five; anything else is treated as flat when loaded). [presetId] is
 * the selected preset (built-in id or "custom:<name>"), null for an unsaved curve.
 */
data class EqualizerSettings(
    val enabled: Boolean = false,
    val bandLevels: List<Int> = emptyList(),
    val presetId: String? = null,
)
