# Auralis Music Player

A production-oriented local Android music player built with Kotlin, Jetpack Compose, Material 3, Room, DataStore and Media3.

## Highlights

- Folder import via Storage Access Framework with persisted read access
- Recursive scanner for MP3, FLAC, WAV, M4A/AAC, OGG and OPUS
- Metadata and embedded artwork extraction
- URI-based duplicate prevention plus lightweight fingerprint reconciliation
- Multi-disc aware album ordering
- Background playback through Media3 MediaSessionService
- Full player, mini player and editable queue
- Device-backed Equalizer and LoudnessEnhancer preamp when exposed by the audio engine
- Persisted EQ enable/preamp/band settings
- Playlists, favorites, recent and most-played collections
- English and Persian locale switching with RTL layout support
- Dark, light and system themes with configurable accent color
- Custom sleep timer with visible remaining time

## Technology baseline

- Android Gradle Plugin 9.4.1
- Kotlin 2.4.20
- Compile/Target SDK 37
- Compose BOM 2026.09.00
- Room 2.8.5
- DataStore Preferences 1.2.1
- Media3 1.11.1

## Minimum Android version

Android 8.0 (API 26)

## Open

Open the `AuralisMusicPlayer` directory in Android Studio. Android Studio should use the Gradle wrapper configuration in `gradle/wrapper/gradle-wrapper.properties` and download the configured Gradle distribution when network access is available.

## Validation note

The project was statically checked for Kotlin brace balance, XML well-formedness and placeholder/TODO markers. A full Gradle compile could not be completed in the execution environment because external DNS access to `services.gradle.org` was unavailable, so no claim is made that this exact snapshot has been device-tested here.

## Known scope limits

The implementation keeps crossfade/replay-gain automatic processing unexposed rather than presenting fake controls. Persian resource switching and RTL are wired, but some screen copy is still literal English in the Compose source and should be moved into `strings.xml` before calling the localization pass complete.
