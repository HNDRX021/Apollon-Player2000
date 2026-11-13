package com.auralis.player.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanProgressThrottleTest {
    @Test
    fun emitsFirstProgressImmediatelyThenThrottles() {
        val throttle = ScanProgressThrottle(minIntervalMs = 100, minFileDelta = 16)

        assertTrue(throttle.shouldEmit(1, 1_000))
        assertFalse(throttle.shouldEmit(2, 1_050))
        assertTrue(throttle.shouldEmit(2, 1_100))
    }

    @Test
    fun emitsOnFileBatchEvenWhenTimeHasNotElapsed() {
        val throttle = ScanProgressThrottle(minIntervalMs = 100, minFileDelta = 16)

        assertTrue(throttle.shouldEmit(1, 1_000))
        assertTrue(throttle.shouldEmit(17, 1_001))
    }
}
