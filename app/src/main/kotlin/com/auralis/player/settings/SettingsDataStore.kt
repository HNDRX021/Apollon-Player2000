package com.auralis.player.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.auralis.player.audio.EqualizerModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
        val ACCENT_COLOR = longPreferencesKey("accent_color")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val DEEP_DUPLICATE_SCAN = booleanPreferencesKey("deep_duplicate_scan")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_LEVELS = stringPreferencesKey("eq_levels")
        val EQ_PRESET_ID = stringPreferencesKey("eq_preset_id")
        val EQ_CUSTOM_PRESETS = stringPreferencesKey("eq_custom_presets")
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val RESTART_PREVIOUS = booleanPreferencesKey("restart_track_on_previous")
        val SONG_SORT = stringPreferencesKey("song_sort")
        val ALBUM_SORT = stringPreferencesKey("album_sort")
        val ARTIST_SORT = stringPreferencesKey("artist_sort")
        val COMPACT_MODE = booleanPreferencesKey("compact_mode")
        val IGNORED_FOLDERS = stringSetPreferencesKey("ignored_folders")
        val RESPECT_NOMEDIA = booleanPreferencesKey("respect_nomedia")
        val PAUSE_ON_AUDIO_NOISY = booleanPreferencesKey("pause_on_audio_noisy")
        val REPLAY_GAIN_ENABLED = booleanPreferencesKey("replay_gain_enabled")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val DEFAULT_SLEEP_TIMER_MINUTES = intPreferencesKey("default_sleep_timer_minutes")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        val ENABLED_AUDIO_FORMATS = stringSetPreferencesKey("enabled_audio_formats")
        val DUPLICATE_HANDLING_MODE = stringPreferencesKey("duplicate_handling_mode")
        val SHOW_REMAINING_TIME = booleanPreferencesKey("show_remaining_time")
        val PLAYLIST_LIBRARY_SORT = stringPreferencesKey("playlist_library_sort")
        val QUEUE_IDS = stringPreferencesKey("queue_ids")
        val QUEUE_INDEX = intPreferencesKey("queue_index")
        val QUEUE_POSITION = longPreferencesKey("queue_position")
        val QUEUE_SHUFFLE = booleanPreferencesKey("queue_shuffle")
        val QUEUE_REPEAT = intPreferencesKey("queue_repeat")
        val DAILY_PICKS_DAY = stringPreferencesKey("daily_picks_day")
        val DAILY_ALBUM_IDS = stringPreferencesKey("daily_album_ids")
        val DAILY_SONG_IDS = stringPreferencesKey("daily_song_ids")
    }

    // DataStore re-emits on EVERY write to the file, including the playback position checkpoint saved every
    // few seconds while playing, so each derived flow below is de-duplicated: collectors (theme, player,
    // service, Home...) only run again when their own values actually changed.
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            backgroundImageUri = prefs[Keys.BACKGROUND_IMAGE_URI],
            accentColor = prefs[Keys.ACCENT_COLOR] ?: AppSettings.DEFAULT_ACCENT_COLOR,
            languageTag = LocaleApplier.normalize(prefs[Keys.LANGUAGE_TAG]),
            deepDuplicateScan = prefs[Keys.DEEP_DUPLICATE_SCAN] ?: false,
            crossfadeEnabled = prefs[Keys.CROSSFADE_ENABLED] ?: false,
            crossfadeSeconds = (prefs[Keys.CROSSFADE_SECONDS] ?: AppSettings.DEFAULT_CROSSFADE_SECONDS)
                .coerceIn(AppSettings.MIN_CROSSFADE_SECONDS, AppSettings.MAX_CROSSFADE_SECONDS),
            restartTrackOnPrevious = prefs[Keys.RESTART_PREVIOUS] ?: true,
            songSort = prefs[Keys.SONG_SORT]?.let { runCatching { SongSort.valueOf(it) }.getOrNull() }
                ?: SongSort.TITLE,
            albumSort = prefs[Keys.ALBUM_SORT]?.let { runCatching { AlbumSort.valueOf(it) }.getOrNull() }
                ?: AlbumSort.TITLE,
            artistSort = prefs[Keys.ARTIST_SORT]?.let { runCatching { ArtistSort.valueOf(it) }.getOrNull() }
                ?: ArtistSort.NAME,
            compactMode = prefs[Keys.COMPACT_MODE] ?: false,
            ignoredFolderNames = prefs[Keys.IGNORED_FOLDERS] ?: emptySet(),
            respectNoMedia = prefs[Keys.RESPECT_NOMEDIA] ?: true,
            pauseOnAudioBecomingNoisy = prefs[Keys.PAUSE_ON_AUDIO_NOISY] ?: true,
            replayGainEnabled = prefs[Keys.REPLAY_GAIN_ENABLED] ?: false,
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: false,
            defaultSleepTimerMinutes = (prefs[Keys.DEFAULT_SLEEP_TIMER_MINUTES] ?: AppSettings.DEFAULT_SLEEP_TIMER_MINUTES)
                .coerceIn(5, 180),
            enabledAudioFormats = prefs[Keys.ENABLED_AUDIO_FORMATS] ?: AppSettings.DEFAULT_AUDIO_FORMATS,
            duplicateHandlingMode = prefs[Keys.DUPLICATE_HANDLING_MODE]?.let { runCatching { DuplicateHandlingMode.valueOf(it) }.getOrNull() }
                ?: if (prefs[Keys.DEEP_DUPLICATE_SCAN] == true) DuplicateHandlingMode.SKIP_IDENTICAL_CONTENT else DuplicateHandlingMode.SAF_IDENTITY_ONLY,
            showRemainingTime = prefs[Keys.SHOW_REMAINING_TIME] ?: false,
        )
    }.distinctUntilChanged()

    val equalizer: Flow<EqualizerSettings> = context.settingsDataStore.data.map { prefs ->
        EqualizerSettings(
            enabled = prefs[Keys.EQ_ENABLED] ?: false,
            bandLevels = prefs[Keys.EQ_LEVELS]
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                .orEmpty(),
            presetId = prefs[Keys.EQ_PRESET_ID],
        )
    }.distinctUntilChanged()

    val savedEqPresets: Flow<List<SavedEqPreset>> = context.settingsDataStore.data.map { prefs ->
        SavedEqPresetCodec.decode(prefs[Keys.EQ_CUSTOM_PRESETS])
    }.distinctUntilChanged()

    val searchHistory: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.SEARCH_HISTORY].orEmpty().split('\n').map(String::trim).filter(String::isNotBlank).take(10)
    }.distinctUntilChanged()

    suspend fun rememberSearch(query: String) {
        val cleaned = query.trim().replace('\n', ' ').take(80)
        if (cleaned.isBlank()) return
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.SEARCH_HISTORY].orEmpty().split('\n').map(String::trim).filter(String::isNotBlank)
            prefs[Keys.SEARCH_HISTORY] = (listOf(cleaned) + current.filterNot { it.equals(cleaned, ignoreCase = true) }).take(10).joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        context.settingsDataStore.edit { it.remove(Keys.SEARCH_HISTORY) }
    }

    val playbackSnapshot: Flow<PlaybackSnapshot> = context.settingsDataStore.data.map { prefs ->
        PlaybackSnapshot(
            songIds = prefs[Keys.QUEUE_IDS]?.split(",")?.mapNotNull { it.trim().toLongOrNull() }.orEmpty(),
            index = prefs[Keys.QUEUE_INDEX] ?: 0,
            positionMs = prefs[Keys.QUEUE_POSITION] ?: 0L,
            shuffleEnabled = prefs[Keys.QUEUE_SHUFFLE] ?: false,
            repeatMode = prefs[Keys.QUEUE_REPEAT] ?: 0,
        )
    }

    val dailyPicks: Flow<DailyPicks> = context.settingsDataStore.data.map { prefs ->
        DailyPicks(
            dayKey = prefs[Keys.DAILY_PICKS_DAY],
            albumIds = prefs[Keys.DAILY_ALBUM_IDS].toIdList(),
            songIds = prefs[Keys.DAILY_SONG_IDS].toIdList(),
        )
    }.distinctUntilChanged()

    suspend fun saveDailyPicks(picks: DailyPicks) {
        context.settingsDataStore.edit {
            val day = picks.dayKey
            if (day == null) it.remove(Keys.DAILY_PICKS_DAY) else it[Keys.DAILY_PICKS_DAY] = day
            it[Keys.DAILY_ALBUM_IDS] = picks.albumIds.joinToString(",")
            it[Keys.DAILY_SONG_IDS] = picks.songIds.joinToString(",")
        }
    }

    suspend fun savePlaybackSnapshot(snapshot: PlaybackSnapshot) {
        context.settingsDataStore.edit {
            it[Keys.QUEUE_IDS] = snapshot.songIds.joinToString(",")
            it[Keys.QUEUE_INDEX] = snapshot.index
            it[Keys.QUEUE_POSITION] = snapshot.positionMs
            it[Keys.QUEUE_SHUFFLE] = snapshot.shuffleEnabled
            it[Keys.QUEUE_REPEAT] = snapshot.repeatMode
        }
    }

    /**
     * The cheap periodic checkpoint while playing: only the current index and position change, so the
     * (potentially thousands of ids long) queue string is not rebuilt and rewritten every few seconds.
     * Queue, shuffle and repeat changes still go through [savePlaybackSnapshot].
     */
    suspend fun savePlaybackPosition(index: Int, positionMs: Long) {
        context.settingsDataStore.edit {
            it[Keys.QUEUE_INDEX] = index
            it[Keys.QUEUE_POSITION] = positionMs
        }
    }

    suspend fun setEqualizer(settings: EqualizerSettings) {
        context.settingsDataStore.edit {
            it[Keys.EQ_ENABLED] = settings.enabled
            it[Keys.EQ_LEVELS] = settings.bandLevels.joinToString(",")
            val presetId = settings.presetId
            if (presetId == null) it.remove(Keys.EQ_PRESET_ID) else it[Keys.EQ_PRESET_ID] = presetId
        }
    }

    /**
     * Checks and writes in one DataStore transaction, so a duplicate name can never
     * overwrite an existing preset unless [replaceExisting] is explicitly true.
     */
    suspend fun saveEqPreset(name: String, bandLevelsMb: List<Int>, replaceExisting: Boolean = false): EqualizerModel.SaveResult {
        var result: EqualizerModel.SaveResult = EqualizerModel.SaveResult.EmptyName
        context.settingsDataStore.edit { prefs ->
            val existing = SavedEqPresetCodec.decode(prefs[Keys.EQ_CUSTOM_PRESETS])
            val (outcome, updated) = EqualizerModel.savePreset(existing, name, bandLevelsMb, replaceExisting)
            result = outcome
            if (outcome == EqualizerModel.SaveResult.Saved) {
                prefs[Keys.EQ_CUSTOM_PRESETS] = SavedEqPresetCodec.encode(updated)
            }
        }
        return result
    }

    suspend fun deleteEqPreset(name: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.EQ_CUSTOM_PRESETS] = SavedEqPresetCodec.encode(
                SavedEqPresetCodec.decode(prefs[Keys.EQ_CUSTOM_PRESETS]).filterNot { it.name == name },
            )
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setBackgroundImageUri(uri: String?) {
        context.settingsDataStore.edit {
            if (uri == null) it.remove(Keys.BACKGROUND_IMAGE_URI)
            else it[Keys.BACKGROUND_IMAGE_URI] = uri
        }
    }

    suspend fun setAccentColor(argb: Long) {
        context.settingsDataStore.edit { it[Keys.ACCENT_COLOR] = argb }
    }

    suspend fun setLanguageTag(tag: String) {
        context.settingsDataStore.edit { it[Keys.LANGUAGE_TAG] = tag }
    }

    suspend fun setDeepDuplicateScan(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DEEP_DUPLICATE_SCAN] = enabled }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.settingsDataStore.edit {
            it[Keys.CROSSFADE_SECONDS] = seconds.coerceIn(AppSettings.MIN_CROSSFADE_SECONDS, AppSettings.MAX_CROSSFADE_SECONDS)
        }
    }

    suspend fun setCompactMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.COMPACT_MODE] = enabled }
    }

    suspend fun setRespectNoMedia(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.RESPECT_NOMEDIA] = enabled }
    }

    suspend fun setPauseOnAudioBecomingNoisy(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.PAUSE_ON_AUDIO_NOISY] = enabled }
    }

    suspend fun setReplayGainEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.REPLAY_GAIN_ENABLED] = enabled }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DYNAMIC_COLOR_ENABLED] = enabled }
    }

    suspend fun setDefaultSleepTimerMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_SLEEP_TIMER_MINUTES] = minutes.coerceIn(5, 180) }
    }

    suspend fun setAudioFormatEnabled(extension: String, enabled: Boolean) {
        val normalized = extension.trim().lowercase().removePrefix(".")
        if (normalized !in AppSettings.DEFAULT_AUDIO_FORMATS) return
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_AUDIO_FORMATS] ?: AppSettings.DEFAULT_AUDIO_FORMATS
            prefs[Keys.ENABLED_AUDIO_FORMATS] = if (enabled) current + normalized else current - normalized
        }
    }

    suspend fun setDuplicateHandlingMode(mode: DuplicateHandlingMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DUPLICATE_HANDLING_MODE] = mode.name
            prefs[Keys.DEEP_DUPLICATE_SCAN] = mode == DuplicateHandlingMode.SKIP_IDENTICAL_CONTENT
        }
    }

    suspend fun addIgnoredFolder(name: String) {
        val cleaned = name.trim().lowercase()
        if (cleaned.isEmpty()) return
        context.settingsDataStore.edit { it[Keys.IGNORED_FOLDERS] = (it[Keys.IGNORED_FOLDERS] ?: emptySet()) + cleaned }
    }

    suspend fun removeIgnoredFolder(name: String) {
        context.settingsDataStore.edit { it[Keys.IGNORED_FOLDERS] = (it[Keys.IGNORED_FOLDERS] ?: emptySet()) - name }
    }

    suspend fun setSongSort(sort: SongSort) {
        context.settingsDataStore.edit { it[Keys.SONG_SORT] = sort.name }
    }

    suspend fun setAlbumSort(sort: AlbumSort) { context.settingsDataStore.edit { it[Keys.ALBUM_SORT] = sort.name } }
    suspend fun setArtistSort(sort: ArtistSort) { context.settingsDataStore.edit { it[Keys.ARTIST_SORT] = sort.name } }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.CROSSFADE_ENABLED] = enabled }
    }

    suspend fun setRestartTrackOnPrevious(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.RESTART_PREVIOUS] = enabled }
    }

    suspend fun setShowRemainingTime(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_REMAINING_TIME] = enabled }
    }

    /** The Playlists tab's sort (the enum name of PlaylistLibrarySort), or null before the user ever chose one. */
    val playlistLibrarySort: Flow<String?> = context.settingsDataStore.data.map { it[Keys.PLAYLIST_LIBRARY_SORT] }.distinctUntilChanged()

    suspend fun setPlaylistLibrarySort(name: String) {
        context.settingsDataStore.edit { it[Keys.PLAYLIST_LIBRARY_SORT] = name }
    }
}

private fun String?.toIdList(): List<Long> =
    this?.split(",")?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()
