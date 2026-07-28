package com.neonbeat.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [LrcParser]. */
class LrcParserTest {

    @Test
    fun `parses simple timestamped lines in order`() {
        val lines = LrcParser.parse(
            """
            [00:12.50]First line
            [00:05.00]Earlier line
            """.trimIndent(),
        )

        assertEquals(2, lines.size)
        assertEquals(5_000L, lines[0].timeMs)
        assertEquals("Earlier line", lines[0].text)
        assertEquals(12_500L, lines[1].timeMs)
    }

    @Test
    fun `expands repeated timestamps on one line`() {
        val lines = LrcParser.parse("[00:10.00][01:00.00]Chorus")

        assertEquals(2, lines.size)
        assertTrue(lines.all { it.text == "Chorus" })
        assertEquals(listOf(10_000L, 60_000L), lines.map { it.timeMs })
    }

    @Test
    fun `applies offset tag`() {
        val lines = LrcParser.parse(
            """
            [offset:500]
            [00:10.00]Shifted
            """.trimIndent(),
        )

        assertEquals(9_500L, lines.single().timeMs)
    }

    @Test
    fun `offset never produces a negative timestamp`() {
        val lines = LrcParser.parse(
            """
            [offset:5000]
            [00:01.00]Early
            """.trimIndent(),
        )

        assertEquals(0L, lines.single().timeMs)
    }

    @Test
    fun `handles three digit fractions and long durations`() {
        val lines = LrcParser.parse("[123:45.678]Long track")

        assertEquals(123 * 60_000L + 45_000L + 678L, lines.single().timeMs)
    }

    @Test
    fun `skips metadata tags`() {
        val lines = LrcParser.parse(
            """
            [ar:Some Artist]
            [ti:Some Title]
            [00:01.00]Only line
            """.trimIndent(),
        )

        assertEquals(1, lines.size)
        assertEquals("Only line", lines.single().text)
    }

    @Test
    fun `returns unsynced lines when no timestamps are present`() {
        val lines = LrcParser.parse("Plain line one\nPlain line two")

        assertEquals(2, lines.size)
        assertNull(lines[0].timeMs)
        assertEquals("Plain line one", lines[0].text)
    }

    @Test
    fun `blank input yields no lines`() {
        assertTrue(LrcParser.parse("   ").isEmpty())
    }

    @Test
    fun `active line index tracks playback position`() {
        val lines = LrcParser.parse(
            """
            [00:00.00]One
            [00:10.00]Two
            [00:20.00]Three
            """.trimIndent(),
        )

        assertEquals(0, LrcParser.activeLineIndex(lines, 0))
        assertEquals(0, LrcParser.activeLineIndex(lines, 9_999))
        assertEquals(1, LrcParser.activeLineIndex(lines, 10_000))
        assertEquals(2, LrcParser.activeLineIndex(lines, 99_000))
    }

    @Test
    fun `active line index is minus one for empty lyrics`() {
        assertEquals(-1, LrcParser.activeLineIndex(emptyList(), 1_000))
    }

    @Test
    fun `round trips through toLrc`() {
        val original = "[00:01.50]Hello\n[00:03.25]World"
        val reparsed = LrcParser.parse(LrcParser.toLrc(LrcParser.parse(original)))

        assertEquals(listOf(1_500L, 3_250L), reparsed.map { it.timeMs })
        assertEquals(listOf("Hello", "World"), reparsed.map { it.text })
    }
}
