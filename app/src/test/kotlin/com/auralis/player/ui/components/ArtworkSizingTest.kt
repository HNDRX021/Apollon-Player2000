package com.auralis.player.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkSizingTest {
    @Test
    fun usesTheLargerRenderedDimensionForPortraitAndLandscapeArtwork() {
        assertEquals(640, artworkDecodeTargetPx(widthPx = 300, heightPx = 600))
        assertEquals(640, artworkDecodeTargetPx(widthPx = 600, heightPx = 300))
    }

    @Test
    fun groupsNearbySizesToAvoidReloadingForSmallLayoutChanges() {
        assertEquals(320, artworkDecodeTargetPx(widthPx = 300, heightPx = 300))
        assertEquals(320, artworkDecodeTargetPx(widthPx = 310, heightPx = 310))
    }

    @Test
    fun clampsUnreasonablyLargeDecodeTargets() {
        assertEquals(2048, artworkDecodeTargetPx(widthPx = 5000, heightPx = 4000))
    }

    @Test
    fun returnsOneForInvalidBounds() {
        assertEquals(1, artworkDecodeTargetPx(widthPx = 0, heightPx = -1))
    }
}
