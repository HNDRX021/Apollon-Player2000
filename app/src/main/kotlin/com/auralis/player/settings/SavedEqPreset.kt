package com.auralis.player.settings

import java.nio.charset.StandardCharsets
import java.util.Base64

data class SavedEqPreset(val name: String, val bandLevelsMb: List<Int>)

/** Compact, versionless storage codec for the small user-preset collection. */
object SavedEqPresetCodec {
    fun encode(presets: List<SavedEqPreset>): String = presets.joinToString("|") { preset ->
        val name = Base64.getUrlEncoder().withoutPadding().encodeToString(preset.name.toByteArray(StandardCharsets.UTF_8))
        "$name:${preset.bandLevelsMb.joinToString(",")}" 
    }

    fun decode(value: String?): List<SavedEqPreset> = value.orEmpty().split('|').mapNotNull { row ->
        val separator = row.indexOf(':')
        if (separator <= 0) return@mapNotNull null
        runCatching {
            val name = String(Base64.getUrlDecoder().decode(row.substring(0, separator)), StandardCharsets.UTF_8)
            val levels = row.substring(separator + 1).split(',').map { it.toInt() }
            if (name.isBlank() || levels.isEmpty()) null else SavedEqPreset(name, levels)
        }.getOrNull()
    }
}
