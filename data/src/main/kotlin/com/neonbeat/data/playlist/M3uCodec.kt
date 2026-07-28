package com.neonbeat.data.playlist

import com.neonbeat.core.model.Song
import java.io.File

/** One parsed line of an M3U playlist. */
data class M3uEntry(
    val path: String,
    val title: String? = null,
    val durationSeconds: Long? = null,
)

/**
 * Reader/writer for M3U and M3U8 playlists.
 *
 * Extended M3U (`#EXTINF:`) is both parsed and written, so titles and durations
 * survive a round trip through other players. Relative entries are resolved
 * against the playlist's own directory, which is how nearly every desktop
 * player exports them.
 */
object M3uCodec {

    private const val HEADER = "#EXTM3U"
    private val EXTINF = Regex("^#EXTINF:(-?\\d+)\\s*,\\s*(.*)$")

    /** Parses [file]; unreadable files yield an empty list rather than throwing. */
    fun read(file: File): List<M3uEntry> = runCatching {
        val base = file.parentFile
        val entries = mutableListOf<M3uEntry>()
        var pendingTitle: String? = null
        var pendingDuration: Long? = null

        file.readLines().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#") -> {
                    EXTINF.matchEntire(line)?.let { match ->
                        pendingDuration = match.groupValues[1].toLongOrNull()?.takeIf { it >= 0 }
                        pendingTitle = match.groupValues[2].takeIf { it.isNotBlank() }
                    }
                }
                else -> {
                    val resolved = if (File(line).isAbsolute) line else File(base, line).path
                    entries += M3uEntry(
                        path = resolved,
                        title = pendingTitle,
                        durationSeconds = pendingDuration,
                    )
                    pendingTitle = null
                    pendingDuration = null
                }
            }
        }
        entries
    }.getOrDefault(emptyList())

    /** Writes [songs] as an extended M3U with absolute paths and UTF-8 encoding. */
    fun write(file: File, songs: List<Song>) {
        val content = buildString {
            appendLine(HEADER)
            songs.forEach { song ->
                appendLine("#EXTINF:${song.durationMs / 1000},${song.artist} - ${song.title}")
                appendLine(song.data)
            }
        }
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }
}
