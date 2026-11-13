package com.auralis.player.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How the Library's Songs tab is ordered. TITLE is the "A-Z" order, ARTIST sorts by artist name, ALBUM orders
 * by album, then disc, then track number. YEAR is chronological (oldest first, unknown years last).
 */
enum class SongSort { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION, YEAR }

/** TITLE is "A-Z"; ARTIST sorts by album artist; YEAR is release date, oldest first, unknown last. */
enum class AlbumSort { TITLE, ARTIST, YEAR, RECENT, FAVORITES }

/** NAME is "A-Z"; YEAR orders by each artist's earliest album, oldest first, unknown last. */
enum class ArtistSort { NAME, YEAR, FAVORITES }
enum class DuplicateHandlingMode { SAF_IDENTITY_ONLY, SKIP_IDENTICAL_CONTENT }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Persisted document URI granted by the system photo picker. */
    val backgroundImageUri: String? = null,
    /** ARGB packed as Long so it round-trips cleanly through DataStore's Long preference. */
    val accentColor: Long = DEFAULT_ACCENT_COLOR,
    val languageTag: String = "en",
    /**
     * Opt-in: also hash file contents during scanning to catch the same
     * track duplicated under a different folder/name. Off by default
     * since hashing every file is expensive on a large library.
     */
    val deepDuplicateScan: Boolean = false,
    /** Crossfades automatic (not manual-skip) track transitions. See CrossfadeEngine. */
    val crossfadeEnabled: Boolean = false,
    /** Length of the crossfade overlap; only used while [crossfadeEnabled]. */
    val crossfadeSeconds: Int = DEFAULT_CROSSFADE_SECONDS,
    /** Restart the current track when Previous is pressed near its beginning. */
    val restartTrackOnPrevious: Boolean = true,
    val songSort: SongSort = SongSort.TITLE,
    val albumSort: AlbumSort = AlbumSort.TITLE,
    val artistSort: ArtistSort = ArtistSort.NAME,
    /** Tighter song rows (smaller artwork, less padding). */
    val compactMode: Boolean = false,
    /** Folder names (any depth, case-insensitive) the scanner skips entirely. */
    val ignoredFolderNames: Set<String> = emptySet(),
    /** Skip any folder that contains a `.nomedia` file, like Android's own media scanner. */
    val respectNoMedia: Boolean = true,
    val pauseOnAudioBecomingNoisy: Boolean = true,
    val replayGainEnabled: Boolean = false,
    val dynamicColorEnabled: Boolean = false,
    val defaultSleepTimerMinutes: Int = DEFAULT_SLEEP_TIMER_MINUTES,
    val enabledAudioFormats: Set<String> = DEFAULT_AUDIO_FORMATS,
    val duplicateHandlingMode: DuplicateHandlingMode = DuplicateHandlingMode.SAF_IDENTITY_ONLY,
    /** Now Playing's time display: false shows the track length, true counts down the time remaining ("-2:36"). */
    val showRemainingTime: Boolean = false,
) {
    companion object {
        const val DEFAULT_ACCENT_COLOR: Long = 0xFF7C5CFCL
        const val DEFAULT_CROSSFADE_SECONDS = 8
        const val MIN_CROSSFADE_SECONDS = 2
        const val MAX_CROSSFADE_SECONDS = 12
        const val DEFAULT_SLEEP_TIMER_MINUTES = 30
        val DEFAULT_AUDIO_FORMATS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "3gp")
    }
}
