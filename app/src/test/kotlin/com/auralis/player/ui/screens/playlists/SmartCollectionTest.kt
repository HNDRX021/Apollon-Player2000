package com.auralis.player.ui.screens.playlists

import com.auralis.player.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SmartCollectionTest {

    private fun song(id: Long, title: String, artist: String? = null, album: String? = null) = Song(
        id = id, documentId = "d$id", folderId = 1L, title = title, artistId = null, artistName = artist,
        albumId = null, albumTitle = album, genreId = null, composer = null, year = null,
        trackNumber = null, totalTracks = null, discNumber = 1, totalDiscs = null,
        durationMs = 1000L, isFavorite = false, playCount = 0, lastPlayedAt = null,
    )

    @Test
    fun everyCollectionIsFoundAgainByItsRouteKey() {
        SmartCollection.entries.forEach { assertEquals(it, SmartCollection.fromKey(it.key)) }
        assertNull(SmartCollection.fromKey("nope"))
        assertNull(SmartCollection.fromKey(null))
    }

    @Test
    fun routeKeysAreDistinct() {
        assertEquals(SmartCollection.entries.size, SmartCollection.entries.map { it.key }.toSet().size)
    }

    @Test
    fun findFilterMatchesTitleArtistAndAlbumAndKeepsOrder() {
        val songs = listOf(
            song(1, "Blue Monday", artist = "New Order"),
            song(2, "Heroes", artist = "David Bowie"),
            song(3, "Song", album = "Blue Album"),
        )
        assertEquals(listOf(1L, 3L), songs.matching("blue").map { it.id })
        assertEquals(listOf(2L), songs.matching("  BOWIE ").map { it.id })
        assertSame(songs, songs.matching("   "))
    }
}
