package com.auralis.player.audio

import com.auralis.player.settings.EqualizerSettings
import com.auralis.player.settings.SavedEqPreset
import com.auralis.player.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerUiState(
    val enabled: Boolean = false,
    /** Gain per band in millibels, always exactly [EqualizerModel.BAND_COUNT] values. */
    val bandLevelsMb: List<Int> = EqualizerModel.FLAT_LEVELS_MB,
    /** Id of the one selected preset (built-in id or "custom:<name>"), or null for an unsaved curve. */
    val selectedPresetId: String? = EqualizerModel.FLAT_ID,
    val customPresets: List<SavedEqPreset> = emptyList(),
)

/**
 * Single source of truth for the equalizer. It owns the UI state, persists it,
 * and publishes it to [EqualizerParams], which every player's
 * [EqualizerAudioProcessor] reads while it processes audio. There is exactly
 * one path from a slider to the sound: UI -> this controller -> params ->
 * the processor inside the player's audio sink.
 *
 * Constructed eagerly at app start (see AuralisApplication) so the saved
 * settings are in effect before the first track plays.
 */
@Singleton
class EqualizerController @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val params: EqualizerParams,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var persistJob: Job? = null

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            val saved = settingsDataStore.equalizer.first()
            val custom = EqualizerModel.sanitizeCustom(settingsDataStore.savedEqPresets.first())
            synchronized(lock) {
                val levels = EqualizerModel.sanitize(saved.bandLevels)
                publish(
                    EqualizerUiState(
                        enabled = saved.enabled,
                        bandLevelsMb = levels,
                        selectedPresetId = EqualizerModel.resolveSelection(levels, saved.presetId, custom),
                        customPresets = custom,
                    ),
                )
            }
        }
    }

    /** On/off only bypasses the filters; the band values and selected preset are kept. */
    fun setEnabled(enabled: Boolean) {
        synchronized(lock) { publish(_uiState.value.copy(enabled = enabled)) }
        persistSoon()
    }

    /** Applies a band change immediately; call [persistCurrent] when the drag ends. */
    fun setBandLevel(band: Int, levelMb: Int) {
        synchronized(lock) {
            val state = _uiState.value
            if (band !in 0 until EqualizerModel.BAND_COUNT) return
            val levels = state.bandLevelsMb.toMutableList().also { it[band] = EqualizerModel.clampMb(levelMb) }
            publish(
                state.copy(
                    bandLevelsMb = levels,
                    selectedPresetId = EqualizerModel.resolveSelection(levels, state.selectedPresetId, state.customPresets),
                ),
            )
        }
    }

    fun selectPreset(id: String) {
        synchronized(lock) {
            val state = _uiState.value
            val levels = EqualizerModel.builtInById(id)?.levelsMb
                ?: EqualizerModel.customNameOf(id)
                    ?.let { name -> state.customPresets.firstOrNull { it.name == name }?.bandLevelsMb }
                ?: return
            publish(state.copy(bandLevelsMb = levels, selectedPresetId = id))
        }
        persistSoon()
    }

    fun reset() = selectPreset(EqualizerModel.FLAT_ID)

    /**
     * Saves the current five gains under [name]. A name that is empty, reserved
     * by a built-in preset, or already used is rejected unless [replaceExisting]
     * is true, in which case only the preset with that exact name is overwritten.
     */
    suspend fun saveCurrentPreset(name: String, replaceExisting: Boolean = false): EqualizerModel.SaveResult {
        val levels = _uiState.value.bandLevelsMb
        val result = settingsDataStore.saveEqPreset(name, levels, replaceExisting)
        if (result == EqualizerModel.SaveResult.Saved) {
            val trimmed = name.trim()
            refreshCustomPresets { saved ->
                saved.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { EqualizerModel.customId(it.name) }
            }
        }
        return result
    }

    /** Deletes a user preset. Built-in presets are not in the custom list and can never be deleted. */
    fun deletePreset(name: String) {
        scope.launch {
            settingsDataStore.deleteEqPreset(name)
            refreshCustomPresets { null }
        }
    }

    fun persistCurrent() = persistSoon()

    private suspend fun refreshCustomPresets(preferredId: (List<SavedEqPreset>) -> String?) {
        val custom = EqualizerModel.sanitizeCustom(settingsDataStore.savedEqPresets.first())
        synchronized(lock) {
            val state = _uiState.value
            publish(
                state.copy(
                    customPresets = custom,
                    selectedPresetId = EqualizerModel.resolveSelection(
                        state.bandLevelsMb,
                        preferredId(custom) ?: state.selectedPresetId,
                        custom,
                    ),
                ),
            )
        }
        persistSoon()
    }

    private fun publish(state: EqualizerUiState) {
        _uiState.value = state
        params.update(state.enabled, state.bandLevelsMb)
    }

    /** Debounced, ordered write: only the latest state is written, so writes can never land out of order. */
    private fun persistSoon() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(150)
            val state = _uiState.value
            settingsDataStore.setEqualizer(
                EqualizerSettings(enabled = state.enabled, bandLevels = state.bandLevelsMb, presetId = state.selectedPresetId),
            )
        }
    }
}
