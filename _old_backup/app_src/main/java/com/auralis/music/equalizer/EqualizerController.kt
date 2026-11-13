package com.auralis.music.equalizer

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.auralis.music.core.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EqualizerController(private val settingsRepository: SettingsRepository) {
    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        runCatching {
            equalizer?.release()
            loudness?.release()
            equalizer = Equalizer(0, audioSessionId)
            loudness = LoudnessEnhancer(audioSessionId)
            refreshBands()
            scope.launch {
                val saved = settingsRepository.settings.first()
                val currentBands = _state.value.bands
                val levels = saved.eqLevels
                val restored = if (levels.size == currentBands.size) levels else emptyList()
                if (restored.isNotEmpty()) {
                    currentBands.indices.forEach { index ->
                        val clamped = restored[index].coerceIn(_state.value.minMb, _state.value.maxMb)
                        runCatching { equalizer?.setBandLevel(index.toShort(), clamped.toShort()) }
                    }
                }
                runCatching {
                    equalizer?.enabled = saved.eqEnabled
                    loudness?.setTargetGain(saved.eqPreampMb.coerceIn(-12000, 12000))
                    loudness?.enabled = saved.eqPreampMb != 0
                }
                _state.value = _state.value.copy(
                    enabled = saved.eqEnabled,
                    preampMb = saved.eqPreampMb.coerceIn(-12000, 12000)
                )
                refreshBands()
            }
        }.onFailure {
            equalizer = null
            loudness = null
            _state.value = _state.value.copy(supported = false)
        }
    }

    private fun refreshBands() {
        val eq = equalizer ?: return
        val range = eq.bandLevelRange
        val bands = (0 until eq.numberOfBands).map { index ->
            EqualizerBand(
                band = index.toShort(),
                frequencyHz = eq.getCenterFreq(index.toShort()) / 1000,
                levelMb = eq.getBandLevel(index.toShort()).toInt()
            )
        }
        _state.value = _state.value.copy(
            supported = true,
            minMb = range[0].toInt(),
            maxMb = range[1].toInt(),
            bands = bands
        )
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        _state.value = _state.value.copy(enabled = enabled)
        scope.launch { settingsRepository.setEqEnabled(enabled) }
    }

    fun setBandLevel(index: Int, levelMb: Int) {
        val eq = equalizer ?: return
        val band = index.toShort()
        val clamped = levelMb.coerceIn(_state.value.minMb, _state.value.maxMb)
        runCatching { eq.setBandLevel(band, clamped.toShort()) }
        refreshBands()
        scope.launch { settingsRepository.setEqLevels(_state.value.bands.map { it.levelMb }) }
    }

    fun setPreamp(millibels: Int) {
        val value = millibels.coerceIn(-12000, 12000)
        runCatching {
            loudness?.setTargetGain(value)
            loudness?.enabled = value != 0
        }
        _state.value = _state.value.copy(preampMb = value)
        scope.launch { settingsRepository.setEqPreampMb(value) }
    }

    fun applySystemPreset(index: Short): String? {
        val eq = equalizer ?: return null
        return runCatching {
            eq.usePreset(index)
            refreshBands()
            scope.launch { settingsRepository.setEqLevels(_state.value.bands.map { it.levelMb }) }
            eq.getPresetName(index).toString()
        }.getOrNull()
    }

    fun presetNames(): List<String> = buildList {
        val eq = equalizer ?: return
        repeat(eq.numberOfPresets) { index -> add(eq.getPresetName(index.toShort()).toString()) }
    }

    fun release() {
        equalizer?.release()
        loudness?.release()
        equalizer = null
        loudness = null
    }
}

data class EqualizerBand(
    val band: Short,
    val frequencyHz: Int,
    val levelMb: Int
)

data class EqualizerState(
    val supported: Boolean = true,
    val enabled: Boolean = false,
    val minMb: Int = -1500,
    val maxMb: Int = 1500,
    val preampMb: Int = 0,
    val bands: List<EqualizerBand> = emptyList()
)
