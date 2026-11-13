package com.auralis.player.domain.util

/** Reduces StateFlow churn during large scans while keeping progress responsive. */
class ScanProgressThrottle(
    private val minIntervalMs: Long = 100,
    private val minFileDelta: Int = 16,
) {
    private var lastEmissionMs: Long? = null
    private var lastEmissionProcessed = 0

    fun shouldEmit(processed: Int, nowMs: Long): Boolean {
        val previousTime = lastEmissionMs
        val shouldEmit = previousTime == null ||
            processed - lastEmissionProcessed >= minFileDelta ||
            nowMs - previousTime >= minIntervalMs
        if (shouldEmit) {
            lastEmissionMs = nowMs
            lastEmissionProcessed = processed
        }
        return shouldEmit
    }
}
