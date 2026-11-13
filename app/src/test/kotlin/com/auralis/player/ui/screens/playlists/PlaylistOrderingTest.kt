package com.auralis.player.ui.screens.playlists

import com.auralis.player.domain.model.Playlist
import com.auralis.player.domain.model.PlaylistEntry
import com.auralis.player.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaylistOrderingTest {

    private fun entry(
        entryId: Long,
        title: String,
        artist: String? = null,
        album: String? = null,
        durationMs: Long = 1000L,
        songId: Long = entryId,
    ) = PlaylistEntry(
        entryId = entryId,
        song = Song(
            id = songId, documentId = "d$songId", folderId = 1L, title = title, artistId = null, artistName = artist,
            albumId = null, albumTitle = album, genreId = null, composer = null, year = null,
            trackNumber = null, totalTracks = null, discNumber = 1, totalDiscs = null,
            durationMs = durationMs, isFavorite = false, playCount = 0, lastPlayedAt = null,
        ),
    )

    private fun playlist(id: Long, name: String, updatedAt: Long) =
        Playlist(id = id, name = name, songCount = 0, updatedAt = updatedAt)

    @Test
    fun filterMatchesTitleArtistAndAlbumIgnoringCase() {
        val entries = listOf(
            entry(1, "Blue Monday", artist = "New Order", album = "Power"),
            entry(2, "Heroes", artist = "David Bowie", album = "Heroes"),
            entry(3, "Song", artist = "Someone", album = "Blue Album"),
        )
        assertEquals(listOf(1L, 3L), entries.filteredBy("blue").map { it.entryId })
        assertEquals(listOf(2L), entries.filteredBy("  BOWIE ").map { it.entryId })
        assertEquals(entries, entries.filteredBy("   "))
        assertEquals(emptyList<Long>(), entries.filteredBy("zzz").map { it.entryId })
    }

    @Test
    fun customOrderIsLeftUntouched() {
        val entries = listOf(entry(1, "b"), entry(2, "a"))
        assertSame(entries, entries.sortedFor(PlaylistSort.CUSTOM))
    }

    @Test
    fun titleSortIgnoresCaseAndKeepsDuplicatesInTheirStoredOrder() {
        val entries = listOf(
            entry(1, "Charlie"),
            entry(2, "alpha", songId = 9),
            entry(3, "Bravo"),
            entry(4, "alpha", songId = 9),
        )
        assertEquals(listOf(2L, 4L, 3L, 1L), entries.sortedFor(PlaylistSort.TITLE).map { it.entryId })
    }

    @Test
    fun artistAndAlbumSortsPutMissingValuesLast() {
        val entries = listOf(
            entry(1, "x", artist = null, album = "Z"),
            entry(2, "y", artist = "Zed", album = null),
            entry(3, "z", artist = "Abe", album = "A"),
        )
        assertEquals(listOf(3L, 2L, 1L), entries.sortedFor(PlaylistSort.ARTIST).map { it.entryId })
        assertEquals(listOf(3L, 1L, 2L), entries.sortedFor(PlaylistSort.ALBUM).map { it.entryId })
    }

    @Test
    fun durationSortIsShortestFirst() {
        val entries = listOf(entry(1, "a", durationMs = 300), entry(2, "b", durationMs = 100), entry(3, "c", durationMs = 200))
        assertEquals(listOf(2L, 3L, 1L), entries.sortedFor(PlaylistSort.DURATION).map { it.entryId })
    }

    @Test
    fun movedItemReordersAndIgnoresBadIndices() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), list.movedItem(0, 2))
        assertEquals(listOf("a", "d", "b", "c"), list.movedItem(3, 1))
        assertEquals(listOf("a", "b", "c", "d"), list.movedItem(1, 1))
        assertSame(list, list.movedItem(-1, 2))
        assertSame(list, list.movedItem(1, 4))
    }

    @Test
    fun libraryOrderIsRecentFirstOrAlphabeticalAndFiltersByName() {
        val playlists = listOf(playlist(1, "road trip", 10), playlist(2, "Chill", 30), playlist(3, "Workout", 20))
        assertEquals(listOf(2L, 3L, 1L), playlists.forLibrary(PlaylistLibrarySort.RECENTS, "").map { it.id })
        assertEquals(listOf(2L, 1L, 3L), playlists.forLibrary(PlaylistLibrarySort.ALPHABETICAL, "").map { it.id })
        assertEquals(listOf(3L), playlists.forLibrary(PlaylistLibrarySort.RECENTS, "work").map { it.id })
    }

    @Test
    fun totalDurationIsReadable() {
        assertEquals("", formatTotalDuration(0))
        assertEquals("1 min", formatTotalDuration(20_000))
        assertEquals("45 min", formatTotalDuration(45 * 60_000L))
        assertEquals("1 hr", formatTotalDuration(60 * 60_000L))
        assertEquals("2 hr 5 min", formatTotalDuration(125 * 60_000L))
    }
}
