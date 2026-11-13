package com.auralis.music.core

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.auralis.music.data.LibraryDatabase
import com.auralis.music.data.LibraryRepository
import com.auralis.music.equalizer.EqualizerController
import com.auralis.music.playback.PlaybackManager
import com.auralis.music.scanner.MusicScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val accent = stringPreferencesKey("accent")
        val artworkAnimation = booleanPreferencesKey("artwork_animation")
        val uiAnimations = booleanPreferencesKey("ui_animations")
        val resumePlayback = booleanPreferencesKey("resume_playback")
        val speed = floatPreferencesKey("speed")
        val notificationArtwork = booleanPreferencesKey("notification_artwork")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val eqPreampMb = androidx.datastore.preferences.core.intPreferencesKey("eq_preamp_mb")
        val eqLevels = stringPreferencesKey("eq_levels")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            theme = p[Keys.theme] ?: "dark",
            language = p[Keys.language] ?: "en",
            accent = p[Keys.accent] ?: "#D7FF4F",
            artworkAnimation = p[Keys.artworkAnimation] ?: true,
            uiAnimations = p[Keys.uiAnimations] ?: true,
            resumePlayback = p[Keys.resumePlayback] ?: true,
            speed = p[Keys.speed] ?: 1f,
            notificationArtwork = p[Keys.notificationArtwork] ?: true,
            eqEnabled = p[Keys.eqEnabled] ?: false,
            eqPreampMb = p[Keys.eqPreampMb] ?: 0,
            eqLevels = p[Keys.eqLevels].orEmpty().split(",").mapNotNull { it.toIntOrNull() }
        )
    }

    suspend fun setTheme(value: String) = update(Keys.theme, value)
    suspend fun setLanguage(value: String) = update(Keys.language, value)
    suspend fun setAccent(value: String) = update(Keys.accent, value)
    suspend fun setArtworkAnimation(value: Boolean) = update(Keys.artworkAnimation, value)
    suspend fun setUiAnimations(value: Boolean) = update(Keys.uiAnimations, value)
    suspend fun setResumePlayback(value: Boolean) = update(Keys.resumePlayback, value)
    suspend fun setSpeed(value: Float) = update(Keys.speed, value)
    suspend fun setNotificationArtwork(value: Boolean) = update(Keys.notificationArtwork, value)
    suspend fun setEqEnabled(value: Boolean) = update(Keys.eqEnabled, value)
    suspend fun setEqPreampMb(value: Int) = update(Keys.eqPreampMb, value)
    suspend fun setEqLevels(value: List<Int>) = update(Keys.eqLevels, value.joinToString(","))

    private suspend inline fun <reified T> update(key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }
}

data class AppSettings(
    val theme: String,
    val language: String,
    val accent: String,
    val artworkAnimation: Boolean,
    val uiAnimations: Boolean,
    val resumePlayback: Boolean,
    val speed: Float,
    val notificationArtwork: Boolean,
    val eqEnabled: Boolean,
    val eqPreampMb: Int,
    val eqLevels: List<Int>
)

class AppContainer(context: Context) {
    val database = LibraryDatabase.get(context)
    val repository = LibraryRepository(database.dao())
    val settings = SettingsRepository(context)
    val equalizer = EqualizerController(settings)
    val playback = PlaybackManager(context.applicationContext, repository, equalizer, settings)
    val scanner = MusicScanner(context.applicationContext, context.contentResolver, repository, java.io.File(context.filesDir, "artwork"))
}
