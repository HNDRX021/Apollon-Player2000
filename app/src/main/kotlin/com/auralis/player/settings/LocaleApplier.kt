package com.auralis.player.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** The app is English-only; Persian was removed from the language options. */
object LocaleApplier {
    const val DEFAULT_TAG = "en"

    /** Languages offered in Settings. */
    val SUPPORTED_TAGS: List<String> = listOf(DEFAULT_TAG)

    /**
     * Maps any stored or requested tag (including "fa" saved by an older version) to a supported
     * one, so nobody stays stuck on a language the app no longer offers.
     */
    fun normalize(tag: String?): String =
        SUPPORTED_TAGS.firstOrNull { supported -> tag != null && (tag == supported || tag.startsWith("$supported-")) }
            ?: DEFAULT_TAG

    fun apply(languageTag: String) {
        val tag = normalize(languageTag)
        // Skip the call when nothing changes: setting the same locales again can recreate the activity.
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == tag) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
