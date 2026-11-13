package com.auralis.player.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test fun parsesCentisecondTimestampsAndMultipleTimesOnOneLine() {
        assertEquals(
            listOf(LyricLine(12_340L, "Hello"), LyricLine(25_600L, "Hello")),
            LrcParser.parse("[00:12.34][00:25.6]Hello"),
        )
    }
}
