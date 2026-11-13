package com.auralis.player.domain.util

import com.auralis.player.domain.model.Album
import com.auralis.player.domain.model.Song
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumStatsTest {

    private fun album(id: Long, title: String, year: Int? = null, releaseDate: String? = null) = Album(
        id = id, title = title, albumArtistId = 1L, year = year, artworkUri = null,
        trackCount = 10, discCount = 1, releaseDate = releaseDate,
    )

    private fun song(durationMs: Long) = Song(
        id = 1L, documentId = "d", folderId = 1L, title = "t", artistId = null, artistName = null,
        albumId = 1L, albumTitle = null, genreId = null, composer = null, year = null,
        trackNumber = null, totalTracks = null, discNumber = 1, totalDiscs = null,
        durationMs = durationMs, isFavorite = false, playCount = 0, lastPlayedAt = null,
    )

    @Test
    fun albumsAreOrderedOldestFirstWithUndatedLast() {
        val sorted = listOf(
            album(1, "Undated"),
            album(2, "Newest", releaseDate = "2019-05-01"),
            album(3, "Year tag only", year = 2018),
            album(4, "Oldest", releaseDate = "2017"),
        ).sortedWith(albumReleaseOrder)
        assertEquals(listOf("Oldest", "Year tag only", "Newest", "Undated"), sorted.map { it.title })
    }

    @Test
    fun totalDurationSumsRealTrackDurationsAndIgnoresUnknownOnes() {
        val songs = listOf(song(180_000), song(0), song(-5), song(240_000))
        assertEquals(420_000L, albumTotalDurationMs(songs))
        assertEquals(0L, albumTotalDurationMs(emptyList()))
    }

    @Test
    fun durationPartsRoundSensibly() {
        assertNull(albumDurationParts(0))
        assertEquals(AlbumDurationParts(1, 12, 0), albumDurationParts(72 * 60_000L))
        assertEquals(AlbumDurationParts(0, 42, 0), albumDurationParts(42 * 60_000L + 10_000L))
        assertEquals(AlbumDurationParts(0, 7, 32), albumDurationParts(452_000L))
    }

    @Test
    fun releaseDateIsFormattedAtItsRealPrecision() {
        assertEquals("13 October 2024", formatReleaseDate("2024-10-13", Locale.ENGLISH))
        assertEquals("October 2024", formatReleaseDate("2024-10", Locale.ENGLISH))
        assertEquals("2024", formatReleaseDate("2024", Locale.ENGLISH))
        assertEquals("2024", formatReleaseDate("2024-02-31", Locale.ENGLISH))
        assertNull(formatReleaseDate(null, Locale.ENGLISH))
        assertNull(formatReleaseDate("  ", Locale.ENGLISH))
    }
}
