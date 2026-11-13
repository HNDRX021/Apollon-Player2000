package com.auralis.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainParserTest {
    @Test fun parsesReplayGainWithOptionalDbSuffix() {
        assertEquals(-7.4f, ReplayGainParser.parseDb(" -7.4 dB ")!!, 0.001f)
    }

    @Test fun rejectsInvalidAndNonFiniteValues() {
        assertNull(ReplayGainParser.parseDb("loud"))
        assertNull(ReplayGainParser.parseDb("NaN"))
        assertNull(ReplayGainParser.parseDb("Infinity"))
    }

    @Test fun boundsGainToProtectPlaybackHeadroom() {
        assertEquals(12f, ReplayGainParser.parseDb("80 dB")!!, 0f)
        assertEquals(-24f, ReplayGainParser.parseDb("-80 dB")!!, 0f)
    }
}
