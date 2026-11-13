package com.auralis.player.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSelectionTest {
    private val pool = (1L..200L).toList()

    // --- daily picks -------------------------------------------------------------------------

    @Test
    fun sameDayGivesTheSamePicksWhateverOrderTheCandidatesArriveIn() {
        val first = pickDailyIds(pool, 25, "2026-10-21", DAILY_SONG_SALT)
        val again = pickDailyIds(pool.reversed(), 25, "2026-10-21", DAILY_SONG_SALT)
        assertEquals(first, again)
    }

    @Test
    fun aNewDayGivesDifferentPicks() {
        val today = pickDailyIds(pool, 25, "2026-10-21", DAILY_SONG_SALT)
        val tomorrow = pickDailyIds(pool, 25, "2026-10-22", DAILY_SONG_SALT)
        assertNotEquals(today, tomorrow)
    }

    @Test
    fun picksAreUniqueAndCappedAtTheRequestedCount() {
        val picks = pickDailyIds(pool, 25, "2026-10-21", DAILY_SONG_SALT)
        assertEquals(25, picks.size)
        assertEquals(25, picks.toSet().size)
        assertTrue(pool.containsAll(picks))
    }

    @Test
    fun aSmallLibraryReturnsEverythingOnceWithoutErrors() {
        val picks = pickDailyIds(listOf(7L, 7L, 3L), 25, "2026-10-21", DAILY_SONG_SALT)
        assertEquals(setOf(3L, 7L), picks.toSet())
        assertEquals(2, picks.size)
    }

    @Test
    fun emptyLibraryOrZeroCountGivesNoPicks() {
        assertEquals(emptyList<Long>(), pickDailyIds(emptyList(), 10, "2026-10-21", DAILY_ALBUM_SALT))
        assertEquals(emptyList<Long>(), pickDailyIds(pool, 0, "2026-10-21", DAILY_ALBUM_SALT))
    }

    @Test
    fun avoidedIdsAreSkippedWhenThereAreEnoughOthers() {
        val picks = pickDailyIds(listOf(1L, 2L, 3L, 4L, 5L), 3, "2026-10-21", DAILY_ALBUM_SALT, avoid = setOf(1L, 2L))
        assertEquals(setOf(3L, 4L, 5L), picks.toSet())
    }

    @Test
    fun avoidedIdsOnlyFillUpWhenTheLibraryIsTooSmallWithoutThem() {
        val picks = pickDailyIds(listOf(1L, 2L, 3L, 4L, 5L), 5, "2026-10-21", DAILY_ALBUM_SALT, avoid = setOf(1L, 2L))
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), picks.toSet())
        // The three non-avoided albums come first, the avoided ones only after them.
        assertEquals(setOf(3L, 4L, 5L), picks.take(3).toSet())
    }

    // --- Today's Music: full daily order and pagination -------------------------------------

    @Test
    fun todaysMusicKeepsTheSavedFirstPageThenEveryOtherSongExactlyOnce() {
        val first = pickDailyIds(pool, 25, "2026-10-21", DAILY_SONG_SALT)
        val ordered = orderedDailyIds(first, pool, "2026-10-21")
        assertEquals(first, ordered.take(25))
        assertEquals(200, ordered.size)
        assertEquals(pool.toSet(), ordered.toSet())
    }

    @Test
    fun theOrderAfterTheFirstPageIsStableForTheDayWhateverOrderTheLibraryArrivesIn() {
        val first = pickDailyIds(pool, 25, "2026-10-21", DAILY_SONG_SALT)
        assertEquals(
            orderedDailyIds(first, pool, "2026-10-21"),
            orderedDailyIds(first, pool.reversed(), "2026-10-21"),
        )
    }

    @Test
    fun showMoreRevealsTwentyFiveMoreEachTimeKeepingTheOrderUntilEverythingIsShown() {
        val songs = (1..60).toList()
        var visible = TODAYS_MUSIC_PAGE_SIZE
        assertEquals(songs.take(25), songs.firstVisible(visible))
        assertTrue(hasMoreToShow(songs.size, visible))

        visible += TODAYS_MUSIC_PAGE_SIZE
        assertEquals(songs.take(50), songs.firstVisible(visible))
        assertTrue(hasMoreToShow(songs.size, visible))

        // 60 is not a multiple of 25: the last page is just the 10 that remain.
        visible += TODAYS_MUSIC_PAGE_SIZE
        assertEquals(songs, songs.firstVisible(visible))
        assertFalse(hasMoreToShow(songs.size, visible))
    }

    @Test
    fun aTotalThatIsExactlyAMultipleOfThePageSizeEndsWithoutAnEmptyExtraPage() {
        val songs = (1..50).toList()
        assertTrue(hasMoreToShow(songs.size, 25))
        assertFalse(hasMoreToShow(songs.size, 50))
        assertEquals(songs, songs.firstVisible(50))
    }

    @Test
    fun listsShorterThanOnePageShowEverythingWithoutShowMore() {
        val few = (1..10).toList()
        assertEquals(few, few.firstVisible(TODAYS_MUSIC_PAGE_SIZE))
        assertFalse(hasMoreToShow(few.size, TODAYS_MUSIC_PAGE_SIZE))
        assertEquals(emptyList<Int>(), emptyList<Int>().firstVisible(TODAYS_MUSIC_PAGE_SIZE))
        assertFalse(hasMoreToShow(0, TODAYS_MUSIC_PAGE_SIZE))
    }

    // --- Your Favorites ranking --------------------------------------------------------------

    private fun signals(
        id: Long,
        albumFavorite: Boolean = false,
        favoriteSongs: Int = 0,
        playlistSongs: Int = 0,
        recentPlays: Int = 0,
        recentDays: Int = 0,
        totalPlays: Int = 0,
        lastPlayedAt: Long = 0L,
    ) = AlbumSignals(id, albumFavorite, favoriteSongs, playlistSongs, recentPlays, recentDays, totalPlays, lastPlayedAt)

    @Test
    fun aFavoritedAlbumQualifiesEvenWithoutAnyPlays() {
        assertTrue(signals(1, albumFavorite = true).qualifiesAsFavorite())
        assertEquals(listOf(1L), rankFavoriteAlbumIds(listOf(signals(1, albumFavorite = true)), 10))
    }

    @Test
    fun favoritedSongsAndPlaylistSongsEachQualifyAnAlbumWithoutPlays() {
        assertTrue(signals(1, favoriteSongs = 1).qualifiesAsFavorite())
        assertTrue(signals(2, playlistSongs = 1).qualifiesAsFavorite())
    }

    @Test
    fun playbackAloneNeedsSeveralDaysSoOneListenIsNotARecommendation() {
        assertFalse(signals(1, recentPlays = 12, recentDays = 1, totalPlays = 12).qualifiesAsFavorite())
        assertTrue(signals(2, recentPlays = 4, recentDays = 2, totalPlays = 4).qualifiesAsFavorite())
        assertFalse(signals(3).qualifiesAsFavorite())
    }

    @Test
    fun anExplicitlyFavoritedAlbumOutranksEvenTheMostPlayedOne() {
        val favorite = signals(1, albumFavorite = true)
        // Every playback signal at its cap: the most listening can add up to.
        val mostPlayed = signals(2, recentPlays = 500, recentDays = 30, totalPlays = 5_000)
        assertEquals(44.0, mostPlayed.preferenceScore(), 0.0001)
        assertEquals(listOf(1L, 2L), rankFavoriteAlbumIds(listOf(mostPlayed, favorite), 10))
    }

    @Test
    fun moreFavoritedSongsRankHigher() {
        val three = signals(1, favoriteSongs = 3)
        val one = signals(2, favoriteSongs = 1)
        assertEquals(listOf(1L, 2L), rankFavoriteAlbumIds(listOf(one, three), 10))
    }

    @Test
    fun moreUniquePlaylistSongsRankHigher() {
        val four = signals(1, playlistSongs = 4)
        val two = signals(2, playlistSongs = 2)
        assertEquals(listOf(1L, 2L), rankFavoriteAlbumIds(listOf(two, four), 10))
    }

    @Test
    fun similarScoresAreOrderedByMostRecentListening() {
        val older = signals(1, favoriteSongs = 1, lastPlayedAt = 1_000L)
        val newer = signals(2, favoriteSongs = 1, lastPlayedAt = 9_000L)
        assertEquals(listOf(2L, 1L), rankFavoriteAlbumIds(listOf(older, newer), 10))
    }

    @Test
    fun overallPlayCountOnlyCountsOnceItIsReliable() {
        assertEquals(0.0, signals(1, favoriteSongs = 0, totalPlays = 4).preferenceScore(), 0.0001)
        assertEquals(0.25, signals(1, totalPlays = 5).preferenceScore(), 0.0001)
    }

    @Test
    fun eachSignalIsCappedSoNoSingleOneDominates() {
        assertEquals(64.0, signals(1, favoriteSongs = 50).preferenceScore(), 0.0001)
        assertEquals(40.0, signals(1, playlistSongs = 50).preferenceScore(), 0.0001)
    }

    @Test
    fun theRowHoldsAtMostTenUniqueAlbums() {
        val many = (1L..15L).map { signals(it, albumFavorite = true) }
        val ranked = rankFavoriteAlbumIds(many + many, HOME_FAVORITE_ALBUMS)
        assertEquals(10, ranked.size)
        assertEquals(10, ranked.toSet().size)
    }

    @Test
    fun fewerThanTenEligibleAlbumsShowsAllOfThemAndNothingElse() {
        val list = listOf(signals(1, albumFavorite = true), signals(2, playlistSongs = 1), signals(3))
        assertEquals(setOf(1L, 2L), rankFavoriteAlbumIds(list, 10).toSet())
    }

    @Test
    fun noSignalsAtAllGivesAnEmptyRow() {
        assertEquals(emptyList<Long>(), rankFavoriteAlbumIds(emptyList(), 10))
    }
}
