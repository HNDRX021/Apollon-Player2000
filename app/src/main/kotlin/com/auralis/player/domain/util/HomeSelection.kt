package com.auralis.player.domain.util

import com.auralis.player.domain.model.Song
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Albums in Home's "Today's Suggestions". */
const val DAILY_ALBUM_COUNT = 10

/** Songs picked at random and saved as the first page of the day's "Today's Music". */
const val DAILY_SONG_COUNT = 25

/** Most items shown in Home's "Recently Played" row. */
const val HOME_RECENT_ITEMS = 12

/** Most albums shown in Home's "Your Favorites" row. */
const val HOME_FAVORITE_ALBUMS = 10

/**
 * How many of the latest played songs Home reads so that collapsing them into albums still
 * fills the "Recently Played" row (listening to a whole album plays many songs of one album).
 */
const val HOME_RECENT_HISTORY_SONGS = 120

/** Salts that keep the daily album pick and the daily song pick from mirroring each other. */
const val DAILY_ALBUM_SALT = 0x41L
const val DAILY_SONG_SALT = 0x53L

/**
 * Picks up to [count] distinct ids from [candidates] at random, reproducibly for a given
 * [dayKey] (the local calendar date, `yyyy-MM-dd`). Candidates are sorted first, so the outcome
 * never depends on the order the database happened to return them in.
 *
 * Ids in [avoid] are only used to fill up when there are not enough other candidates, so a
 * small library still gets a full row. The result has `min(count, candidates.size)` ids.
 */
fun pickDailyIds(
    candidates: Collection<Long>,
    count: Int,
    dayKey: String,
    salt: Long,
    avoid: Set<Long> = emptySet(),
): List<Long> {
    if (count <= 0) return emptyList()
    val random = Random(dayKey.hashCode().toLong() * 31L + salt)
    val sorted = candidates.distinct().sorted()
    val preferred = sorted.filter { it !in avoid }.shuffled(random)
    if (preferred.size >= count) return preferred.take(count)
    val filler = sorted.filter { it in avoid }.shuffled(random)
    return (preferred + filler).take(count)
}

/** Songs shown at first in "Today's Music", and added by each tap on "Show More". */
const val TODAYS_MUSIC_PAGE_SIZE = 25

/** Salt for ordering the part of the library that follows the day's first page. */
const val DAILY_REST_SALT = 0x67L

/**
 * The whole of "Today's Music" for one day: the day's saved first page ([firstIds], in its saved
 * order) followed by every other song of [allIds] in a random order that stays the same for that
 * [dayKey]. "Show More" reveals this list a page at a time, so the first page never changes
 * and no song appears twice.
 */
fun orderedDailyIds(firstIds: List<Long>, allIds: Collection<Long>, dayKey: String): List<Long> {
    val first = firstIds.distinct()
    val firstSet = first.toHashSet()
    val rest = allIds.filter { it !in firstSet }
    return first + pickDailyIds(rest, rest.size, dayKey, DAILY_REST_SALT)
}

/** The part of a paged list that is currently shown: the first [visibleCount] items (all of them when there are fewer). */
fun <T> List<T>.firstVisible(visibleCount: Int): List<T> = take(visibleCount.coerceAtLeast(0))

/** Whether "Show More" still has something to reveal. */
fun hasMoreToShow(total: Int, visibleCount: Int): Boolean = total > visibleCount

/** Ids of the albums of the most recently played songs, newest first, without duplicates (songs without an album are skipped). */
fun recentAlbumIds(history: List<Song>, limit: Int): List<Long> =
    history.mapNotNull { it.albumId }.distinct().take(limit)

// --- "Your Favorites" ranking ---------------------------------------------------------------

/** "Your Favorites" counts plays from this many days of listening history as "recent". */
const val FAVORITE_WINDOW_DAYS = 30L

/** Playback alone qualifies an album once it was played on at least this many different days within the window. */
const val FAVORITE_MIN_DAYS = 2

// Score weights. Explicit choices (favorites, playlists) outweigh listening; listening is capped
// (at most 44 points in total) so no amount of playing can push an album ahead of one the user
// explicitly favorited (60 points).
private const val ALBUM_FAVORITE_POINTS = 60.0
private const val FAVORITE_SONG_POINTS = 8.0
private const val FAVORITE_SONGS_CAP = 8
private const val PLAYLIST_SONG_POINTS = 5.0
private const val PLAYLIST_SONGS_CAP = 8
private const val RECENT_DAY_POINTS = 3.0
private const val RECENT_DAYS_CAP = 10
private const val RECENT_PLAY_POINTS = 0.3
private const val RECENT_PLAYS_CAP = 30
private const val OVERALL_PLAY_POINTS = 0.05
private const val OVERALL_PLAYS_CAP = 100

/** Overall play counts only count once an album has this many plays; fewer is too little data to say anything. */
private const val OVERALL_PLAYS_RELIABLE_FROM = 5

/** Everything known about one album that says how much the user likes it. All counts are of unique songs or real plays. */
data class AlbumSignals(
    val albumId: Long,
    /** The user favorited the album itself. */
    val isAlbumFavorite: Boolean,
    /** Unique songs of the album that are favorites. */
    val favoriteSongs: Int,
    /** Unique songs of the album that sit in at least one playlist (counted once however many playlists). */
    val playlistSongs: Int,
    /** Plays of the album's songs within the recent window of the history. */
    val recentPlays: Int,
    /** Different calendar days those recent plays happened on. */
    val recentDays: Int,
    /** All-time plays summed over the album's songs. */
    val totalPlays: Int,
    /** Epoch millis of the most recent play of any of the album's songs, 0 if never played. */
    val lastPlayedAt: Long,
)

/**
 * Whether the album may appear in "Your Favorites": it is favorited, has favorited songs, has
 * songs in playlists, or was played on several different days lately. A single listen is not
 * enough, so a thin history never produces made-up recommendations.
 */
fun AlbumSignals.qualifiesAsFavorite(): Boolean =
    isAlbumFavorite || favoriteSongs > 0 || playlistSongs > 0 || recentDays >= FAVORITE_MIN_DAYS

/** Combined preference score: explicit favorites and playlist saves first, then recent and overall playback. */
fun AlbumSignals.preferenceScore(): Double {
    var score = 0.0
    if (isAlbumFavorite) score += ALBUM_FAVORITE_POINTS
    score += FAVORITE_SONG_POINTS * min(favoriteSongs, FAVORITE_SONGS_CAP)
    score += PLAYLIST_SONG_POINTS * min(playlistSongs, PLAYLIST_SONGS_CAP)
    score += RECENT_DAY_POINTS * min(recentDays, RECENT_DAYS_CAP)
    score += RECENT_PLAY_POINTS * min(recentPlays, RECENT_PLAYS_CAP)
    if (totalPlays >= OVERALL_PLAYS_RELIABLE_FROM) score += OVERALL_PLAY_POINTS * min(totalPlays, OVERALL_PLAYS_CAP)
    return score
}

/**
 * The ids of the best [limit] qualifying albums, best first. Scores are compared to the nearest
 * whole point, so near-equal albums are ordered by who was played most recently (then by id, to
 * keep the order stable).
 */
fun rankFavoriteAlbumIds(signals: List<AlbumSignals>, limit: Int): List<Long> =
    signals
        .distinctBy { it.albumId }
        .filter { it.qualifiesAsFavorite() }
        .sortedWith(
            compareByDescending<AlbumSignals> { it.preferenceScore().roundToInt() }
                .thenByDescending { it.lastPlayedAt }
                .thenBy { it.albumId },
        )
        .take(limit)
        .map { it.albumId }
