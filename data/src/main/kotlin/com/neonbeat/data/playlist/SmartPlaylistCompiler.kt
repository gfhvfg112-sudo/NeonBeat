package com.neonbeat.data.playlist

import com.neonbeat.core.model.SmartField
import com.neonbeat.core.model.SmartOperator
import com.neonbeat.core.model.SmartPlaylistRule
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** A compiled smart-playlist query: parameterised SQL plus its bind arguments. */
data class CompiledQuery(val sql: String, val args: List<Any>)

/**
 * Turns smart-playlist rules into SQL.
 *
 * Every user-supplied value is bound as a parameter and every column name is
 * resolved from a fixed allow-list ([SmartField]), so a rule can never inject
 * SQL no matter what the user types into a text rule.
 */
@Singleton
class SmartPlaylistCompiler @Inject constructor() {

    /** Serialises rules for storage in `PlaylistEntity.rulesJson`. */
    fun encodeRules(rules: List<SmartPlaylistRule>, matchAll: Boolean, limit: Int?): String {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(
                JSONObject()
                    .put("field", rule.field.name)
                    .put("operator", rule.operator.name)
                    .put("value", rule.value),
            )
        }
        return JSONObject()
            .put("matchAll", matchAll)
            .put("limit", limit ?: JSONObject.NULL)
            .put("rules", array)
            .toString()
    }

    fun decodeRules(json: String): Triple<List<SmartPlaylistRule>, Boolean, Int?> {
        val root = JSONObject(json)
        val matchAll = root.optBoolean("matchAll", true)
        val limit = if (root.isNull("limit")) null else root.optInt("limit")
        val array = root.optJSONArray("rules") ?: JSONArray()
        val rules = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val field = runCatching { SmartField.valueOf(item.getString("field")) }.getOrNull()
                val operator = runCatching { SmartOperator.valueOf(item.getString("operator")) }.getOrNull()
                if (field != null && operator != null) {
                    add(SmartPlaylistRule(field, operator, item.optString("value")))
                }
            }
        }
        return Triple(rules, matchAll, limit)
    }

    /** Compiles stored rule JSON into an executable query. */
    fun compile(json: String): CompiledQuery {
        val (rules, matchAll, limit) = decodeRules(json)
        return compile(rules, matchAll, limit)
    }

    fun compile(
        rules: List<SmartPlaylistRule>,
        matchAll: Boolean = true,
        limit: Int? = null,
    ): CompiledQuery {
        val args = mutableListOf<Any>()
        val clauses = rules.mapNotNull { rule -> rule.toClause(args) }

        val where = when {
            clauses.isEmpty() -> "1 = 1"
            else -> clauses.joinToString(if (matchAll) " AND " else " OR ") { "($it)" }
        }
        val limitClause = limit?.let { " LIMIT $it" }.orEmpty()

        return CompiledQuery(
            sql = "SELECT * FROM songs WHERE $where ORDER BY title COLLATE NOCASE ASC$limitClause",
            args = args,
        )
    }

    private fun SmartPlaylistRule.toClause(args: MutableList<Any>): String? {
        val column = field.column
        return when (operator) {
            SmartOperator.EQUALS -> {
                args += field.coerce(value)
                "$column = ?"
            }
            SmartOperator.NOT_EQUALS -> {
                args += field.coerce(value)
                "$column != ?"
            }
            SmartOperator.CONTAINS -> {
                args += "%$value%"
                "$column LIKE ? COLLATE NOCASE"
            }
            SmartOperator.NOT_CONTAINS -> {
                args += "%$value%"
                "$column NOT LIKE ? COLLATE NOCASE"
            }
            SmartOperator.STARTS_WITH -> {
                args += "$value%"
                "$column LIKE ? COLLATE NOCASE"
            }
            SmartOperator.GREATER_THAN -> {
                val numeric = value.toDoubleOrNull() ?: return null
                args += numeric
                "$column > ?"
            }
            SmartOperator.LESS_THAN -> {
                val numeric = value.toDoubleOrNull() ?: return null
                args += numeric
                "$column < ?"
            }
            SmartOperator.IN_LAST_DAYS -> {
                val days = value.toLongOrNull() ?: return null
                args += System.currentTimeMillis() - days * MILLIS_PER_DAY
                "$column >= ?"
            }
        }
    }

    /** Numeric fields must bind as numbers, or SQLite compares them as text. */
    private fun SmartField.coerce(value: String): Any = when (this) {
        SmartField.PLAY_COUNT, SmartField.YEAR, SmartField.RATING,
        SmartField.DURATION, SmartField.DATE_ADDED, SmartField.LAST_PLAYED,
        -> value.toDoubleOrNull() ?: 0.0

        SmartField.FAVORITE -> if (value.equals("true", true) || value == "1") 1 else 0
        else -> value
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

/** Maps each rule field to its physical column in the `songs` table. */
private val SmartField.column: String
    get() = when (this) {
        SmartField.TITLE -> "title"
        SmartField.ARTIST -> "artist"
        SmartField.ALBUM -> "album"
        SmartField.GENRE -> "genre"
        SmartField.YEAR -> "year"
        SmartField.DURATION -> "durationMs"
        SmartField.PLAY_COUNT -> "playCount"
        SmartField.RATING -> "rating"
        SmartField.FAVORITE -> "isFavorite"
        SmartField.DATE_ADDED -> "dateAdded"
        SmartField.LAST_PLAYED -> "lastPlayedAt"
        SmartField.FOLDER -> "folderPath"
    }
