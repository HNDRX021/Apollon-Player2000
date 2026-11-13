package com.auralis.player.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumIdentityTest {
    @Test
    fun missingAlbumArtistUsesSharedVariousArtistsIdentity() {
        assertEquals("Various Artists", albumArtistIdentity(null))
    }

    @Test
    fun taggedAlbumArtistRemainsTheGroupingIdentity() {
        assertEquals("The Album Artist", albumArtistIdentity(" The Album Artist "))
    }
}
