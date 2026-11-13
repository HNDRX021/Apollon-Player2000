package com.auralis.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaMenuNavigationTest {
    @Test
    fun omitsNavigationActionsWhenMetadataIsMissing() {
        assertEquals(emptyList<SongMenuDestination>(), songMenuDestinations(null, null))
        assertEquals(listOf(SongMenuDestination.Artist(4L)), songMenuDestinations(4L, null))
        assertEquals(listOf(SongMenuDestination.Album(9L)), songMenuDestinations(null, 9L))
    }

    @Test
    fun returnsBothTargetsForACompleteSong() {
        assertEquals(
            listOf(SongMenuDestination.Artist(4L), SongMenuDestination.Album(9L)),
            songMenuDestinations(4L, 9L),
        )
    }
}
