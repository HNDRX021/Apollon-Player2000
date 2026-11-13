package com.auralis.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderingTest {
    @Test
    fun multiDiscOrderingIsDiscThenTrack() {
        val input = listOf(
            track(2, 1),
            track(1, 2),
            track(1, 1),
            track(2, 2)
        )
        val sorted = input.sortedWith(compareBy<SongEntity> { it.discNumber ?: 1 }.thenBy { it.trackNumber ?: Int.MAX_VALUE })
        assertEquals(listOf("D1-T1", "D1-T2", "D2-T1", "D2-T2"), sorted.map { "D${it.discNumber}-T${it.trackNumber}" })
    }

    private fun track(disc: Int, track: Int) = SongEntity(
        sourceUri = "content://$disc/$track",
        title = "x", artist = "a", album = "b", albumArtist = "a", composer = "", genre = "",
        year = null, trackNumber = track, totalTracks = 2, discNumber = disc, totalDiscs = 2,
        durationMs = 1_000, bitrate = null, sampleRate = null, mimeType = "audio/test", fileName = "x", fileSize = 1,
        modifiedAt = 1, artworkPath = null, fingerprint = "$disc-$track"
    )
}
