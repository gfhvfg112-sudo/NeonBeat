package com.neonbeat.data.playlist

import com.neonbeat.core.model.SmartField
import com.neonbeat.core.model.SmartOperator
import com.neonbeat.core.model.SmartPlaylistRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SmartPlaylistCompiler].
 *
 * The important property under test is that user text always lands in the bind
 * arguments and never in the SQL string.
 */
class SmartPlaylistCompilerTest {

    private val compiler = SmartPlaylistCompiler()

    @Test
    fun `no rules matches everything`() {
        val compiled = compiler.compile(emptyList())

        assertTrue(compiled.sql.contains("WHERE 1 = 1"))
        assertTrue(compiled.args.isEmpty())
    }

    @Test
    fun `contains rule binds a wildcard argument`() {
        val compiled = compiler.compile(
            listOf(SmartPlaylistRule(SmartField.ARTIST, SmartOperator.CONTAINS, "Boards")),
        )

        assertTrue(compiled.sql.contains("artist LIKE ?"))
        assertEquals(listOf<Any>("%Boards%"), compiled.args)
    }

    @Test
    fun `malicious input is bound not interpolated`() {
        val payload = "'; DROP TABLE songs; --"
        val compiled = compiler.compile(
            listOf(SmartPlaylistRule(SmartField.TITLE, SmartOperator.EQUALS, payload)),
        )

        assertTrue(!compiled.sql.contains("DROP TABLE"))
        assertEquals(listOf<Any>(payload), compiled.args)
    }

    @Test
    fun `match all joins with AND and match any joins with OR`() {
        val rules = listOf(
            SmartPlaylistRule(SmartField.GENRE, SmartOperator.EQUALS, "Ambient"),
            SmartPlaylistRule(SmartField.PLAY_COUNT, SmartOperator.GREATER_THAN, "5"),
        )

        assertTrue(compiler.compile(rules, matchAll = true).sql.contains(" AND "))
        assertTrue(compiler.compile(rules, matchAll = false).sql.contains(" OR "))
    }

    @Test
    fun `numeric fields bind as numbers`() {
        val compiled = compiler.compile(
            listOf(SmartPlaylistRule(SmartField.YEAR, SmartOperator.EQUALS, "1998")),
        )

        assertEquals(1998.0, compiled.args.single())
    }

    @Test
    fun `non numeric value for a numeric operator is dropped`() {
        val compiled = compiler.compile(
            listOf(SmartPlaylistRule(SmartField.PLAY_COUNT, SmartOperator.GREATER_THAN, "many")),
        )

        assertTrue(compiled.sql.contains("WHERE 1 = 1"))
        assertTrue(compiled.args.isEmpty())
    }

    @Test
    fun `in last days binds a past timestamp`() {
        val compiled = compiler.compile(
            listOf(SmartPlaylistRule(SmartField.DATE_ADDED, SmartOperator.IN_LAST_DAYS, "7")),
        )

        val bound = compiled.args.single() as Long
        assertTrue(bound < System.currentTimeMillis())
        assertTrue(bound > System.currentTimeMillis() - 8 * 86_400_000L)
    }

    @Test
    fun `limit is applied to the query`() {
        val compiled = compiler.compile(emptyList(), matchAll = true, limit = 25)

        assertTrue(compiled.sql.trimEnd().endsWith("LIMIT 25"))
    }

    @Test
    fun `rules survive an encode decode round trip`() {
        val rules = listOf(
            SmartPlaylistRule(SmartField.ALBUM, SmartOperator.STARTS_WITH, "Music Has"),
            SmartPlaylistRule(SmartField.FAVORITE, SmartOperator.EQUALS, "true"),
        )

        val (decoded, matchAll, limit) = compiler.decodeRules(
            compiler.encodeRules(rules, matchAll = false, limit = 10),
        )

        assertEquals(rules, decoded)
        assertEquals(false, matchAll)
        assertEquals(10, limit)
    }

    @Test
    fun `unknown fields in stored json are ignored`() {
        val json = """{"matchAll":true,"limit":null,"rules":[{"field":"NOPE","operator":"EQUALS","value":"x"}]}"""

        assertTrue(compiler.decodeRules(json).first.isEmpty())
    }
}
