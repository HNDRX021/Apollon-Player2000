package com.auralis.player.ui.screens.detail

import com.auralis.player.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistSongCountTest {
    @Test
    fun countsDistinctTrackIdsOnly() {
        val songs = listOf(song(1), song(2), song(1))
        assertEquals(2, uniqueArtistSongCount(songs))
    }

    @Test
    fun emptyAndUpdatedListsProduceCurrentCount() {
        assertEquals(0, uniqueArtistSongCount(emptyList()))
        assertEquals(2, uniqueArtistSongCount(listOf(song(2), song(3))))
    }

    private fun song(id: Long) = Song(
        id = id,
        documentId = "doc-$id",
        folderId = 1L,
        title = "Track $id",
        artistId = 1L,
        artistName = "Artist",
        albumId = 1L,
        albumTitle = "Album",
        genreId = null,
        composer = null,
        year = null,
        trackNumber = null,
        totalTracks = null,
        discNumber = 1,
        totalDiscs = null,
        durationMs = 1_000L,
        isFavorite = false,
        playCount = 0,
        lastPlayedAt = null,
    )
}
