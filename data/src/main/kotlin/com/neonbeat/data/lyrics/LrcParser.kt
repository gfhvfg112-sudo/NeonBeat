package com.neonbeat.data.lyrics

import com.neonbeat.core.model.LyricLine

/**
 * Parser for LRC lyric files and embedded lyric tags.
 *
 * Supports:
 * - Simple format: `[mm:ss.xx] text`
 * - Multiple timestamps per line: `[00:12.00][01:45.00] chorus`
 * - Two- and three-digit fractional parts
 * - Metadata tags (`[ar:]`, `[ti:]`, `[offset:]`), with `offset` applied
 * - Plain, unsynced text (returned with `null` timestamps)
 */
object LrcParser {

    private val TIME_TAG = Regex("\\[(\\d{1,3}):(\\d{2})([.:]\\d{1,3})?]")
    private val META_TAG = Regex("^\\[(ar|ti|al|au|by|re|ve|length):(.*)]$", RegexOption.IGNORE_CASE)
    private val OFFSET_TAG = Regex("^\\[offset:\\s*([+-]?\\d+)]$", RegexOption.IGNORE_CASE)

    /**
     * Parses [content] into an ordered list of lyric lines.
     *
     * @return Lines sorted by timestamp. If no timestamps are present the input
     *   is returned as unsynced lines in their original order.
     */
    fun parse(content: String): List<LyricLine> {
        if (content.isBlank()) return emptyList()

        var offsetMs = 0L
        val synced = mutableListOf<LyricLine>()
        val plain = mutableListOf<LyricLine>()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            OFFSET_TAG.matchEntire(line)?.let { match ->
                offsetMs = match.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }
            if (META_TAG.matchEntire(line) != null) return@forEach

            val timestamps = TIME_TAG.findAll(line).toList()
            if (timestamps.isEmpty()) {
                plain += LyricLine(timeMs = null, text = line)
                return@forEach
            }

            val text = line.replace(TIME_TAG, "").trim()
            if (text.isEmpty()) return@forEach

            timestamps.forEach { match ->
                synced += LyricLine(timeMs = match.toMillis(), text = text)
            }
        }

        return if (synced.isNotEmpty()) {
            synced.map { it.copy(timeMs = ((it.timeMs ?: 0L) - offsetMs).coerceAtLeast(0L)) }
                .sortedBy { it.timeMs }
        } else {
            plain
        }
    }

    /**
     * Finds the index of the line that should be highlighted at [positionMs].
     *
     * Uses binary search so auto-scroll stays free even for very long lyrics.
     *
     * @return Index into [lines], or -1 before the first timestamp.
     */
    fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            val time = lines[mid].timeMs ?: return -1
            if (time <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /** Serialises lines back to LRC, used when exporting manually edited lyrics. */
    fun toLrc(lines: List<LyricLine>): String = lines.joinToString("\n") { line ->
        val time = line.timeMs
        if (time == null) {
            line.text
        } else {
            val minutes = time / 60_000
            val seconds = (time % 60_000) / 1000
            val hundredths = (time % 1000) / 10
            "[%02d:%02d.%02d]%s".format(minutes, seconds, hundredths, line.text)
        }
    }

    private fun MatchResult.toMillis(): Long {
        val minutes = groupValues[1].toLong()
        val seconds = groupValues[2].toLong()
        val fractionRaw = groupValues[3].removePrefix(".").removePrefix(":")
        val fraction = when (fractionRaw.length) {
            0 -> 0L
            1 -> fractionRaw.toLong() * 100
            2 -> fractionRaw.toLong() * 10
            else -> fractionRaw.take(3).toLong()
        }
        return minutes * 60_000 + seconds * 1000 + fraction
    }
}
