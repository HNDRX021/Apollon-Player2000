package com.auralis.player.domain.util

/** A single timed lyric line. [timeMs] is the start time in milliseconds. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Parser for synchronized lyrics in the LRC format.
 *
 * Supports:
 *  - several timestamps on one line: `[00:12.34][00:25.6]Hello`
 *  - 1-3 digit fractions (`.6` = 600 ms, `.34` = 340 ms, `.345` = 345 ms)
 *  - timestamps without a fraction: `[01:05]`
 *  - ignores metadata tags such as `[ar:Artist]` / `[ti:Title]`
 *
 * The result is sorted by time.
 */
object LrcParser {
    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): List<LyricLine> {
        val result = ArrayList<LyricLine>()
        for (line in raw.lineSequence()) {
            val trimmed = line.trim().trimStart('\uFEFF')
            if (trimmed.isEmpty()) continue

            val matches = TIMESTAMP.findAll(trimmed).toList()
            if (matches.isEmpty()) continue

            val text = trimmed.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val fraction = m.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.toLong()
                }
                result += LyricLine(minutes * 60_000L + seconds * 1_000L + millis, text)
            }
        }
        return result.sortedBy { it.timeMs }
    }
}
