package com.auralis.player.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumAddMessagesTest {
    @Test
    fun playlistMessageWhenEverythingIsNew() {
        assertEquals("Added 12 songs to Road Trip", albumToPlaylistMessage(added = 12, total = 12, playlistName = "Road Trip"))
        assertEquals("Added to Road Trip", albumToPlaylistMessage(added = 1, total = 1, playlistName = "Road Trip"))
    }

    @Test
    fun playlistMessageReportsSkippedDuplicates() {
        assertEquals(
            "Added 3 songs to Road Trip (9 already there)",
            albumToPlaylistMessage(added = 3, total = 12, playlistName = "Road Trip"),
        )
        assertEquals(
            "Added 1 song to Road Trip (11 already there)",
            albumToPlaylistMessage(added = 1, total = 12, playlistName = "Road Trip"),
        )
    }

    @Test
    fun playlistMessageWhenNothingIsNew() {
        assertEquals("Already in Road Trip", albumToPlaylistMessage(added = 0, total = 12, playlistName = "Road Trip"))
    }

    @Test
    fun favoritesMessages() {
        assertEquals("Added 10 songs to Favorites", albumToFavoritesMessage(added = 10, total = 10))
        assertEquals("Added 1 song to Favorites", albumToFavoritesMessage(added = 1, total = 1))
        assertEquals("Added 4 songs to Favorites (6 already there)", albumToFavoritesMessage(added = 4, total = 10))
        assertEquals("Already in Favorites", albumToFavoritesMessage(added = 0, total = 10))
    }
}
