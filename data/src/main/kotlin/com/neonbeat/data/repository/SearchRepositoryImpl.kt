package com.neonbeat.data.repository

import com.neonbeat.core.common.di.IoDispatcher
import com.neonbeat.core.database.dao.PlaylistDao
import com.neonbeat.core.database.dao.SongDao
import com.neonbeat.core.model.Album
import com.neonbeat.core.model.Artist
import com.neonbeat.core.model.Genre
import com.neonbeat.core.model.Song
import com.neonbeat.data.mapper.toFolder
import com.neonbeat.data.mapper.toPlaylist
import com.neonbeat.data.mapper.toSong
import com.neonbeat.domain.repository.SearchRepository
import com.neonbeat.domain.repository.SearchResults
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTS4-backed instant search.
 *
 * One MATCH query returns the song hits, and the album, artist and genre
 * sections are folded out of that same result set in memory. That is a single
 * indexed read per keystroke instead of six, which is what keeps typing inside
 * a frame budget on a 100k-song library. Folders and playlists are small enough
 * to filter from their observable lists, so they also stay reactive: renaming a
 * playlist updates the visible results without re-running the search.
 *
 * Every section is capped at [SECTION_LIMIT]; users refine the query rather
 * than scroll thousands of matches.
 */
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SearchRepository {

    private val recent = MutableStateFlow<List<String>>(emptyList())

    override fun search(query: String): Flow<SearchResults> {
        val token = sanitize(query)
        if (token.isEmpty()) return MutableStateFlow(SearchResults())

        rememberQuery(query)
        val needle = query.trim()

        return combine(songDao.folders(), playlistDao.playlists()) { folders, playlists ->
            folders to playlists
        }.map { (folders, playlists) ->
            val songs = songDao.search(token, SONG_FETCH_LIMIT).map { it.toSong() }
            SearchResults(
                songs = songs.take(SECTION_LIMIT),
                albums = songs.toAlbums(),
                artists = songs.toArtists(),
                genres = songs.toGenres(),
                folders = folders
                    .filter { it.path.contains(needle, ignoreCase = true) }
                    .take(SECTION_LIMIT)
                    .map { it.toFolder() },
                playlists = playlists
                    .filter { it.name.contains(needle, ignoreCase = true) }
                    .take(SECTION_LIMIT)
                    .map { it.toPlaylist() },
            )
        }.flowOn(ioDispatcher)
    }

    override fun recentQueries(): Flow<List<String>> = recent.map { it.take(RECENT_LIMIT) }

    override suspend fun clearRecentQueries() {
        recent.value = emptyList()
    }

    private fun rememberQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        recent.value = (listOf(trimmed) + recent.value.filterNot { it.equals(trimmed, true) })
            .take(RECENT_LIMIT)
    }

    private companion object {
        const val SECTION_LIMIT = 20
        const val RECENT_LIMIT = 10

        /** Wide enough that grouping still finds every album behind the top hits. */
        const val SONG_FETCH_LIMIT = 200

        /**
         * Converts free text into a safe FTS MATCH expression.
         *
         * FTS treats characters such as `"`, `*`, `-` and `:` as operators, so raw
         * user input can throw a syntax error mid-typing. Every token is stripped
         * of operators and given a trailing `*` for prefix matching, which is what
         * makes results appear while the word is still being typed.
         */
        fun sanitize(raw: String): String = raw
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "$it*" }
    }
}

private fun List<Song>.toAlbums(): List<Album> = groupBy { it.albumId }
    .entries
    .take(20)
    .map { (albumId, tracks) ->
        val first = tracks.first()
        Album(
            id = albumId,
            title = first.album,
            artist = first.albumArtist ?: first.artist,
            artistId = first.artistId,
            year = first.year,
            songCount = tracks.size,
            durationMs = tracks.sumOf { it.durationMs },
            artworkUri = tracks.firstNotNullOfOrNull { it.artworkUri },
            dateAddedSeconds = tracks.maxOf { it.dateAddedSeconds },
        )
    }

private fun List<Song>.toArtists(): List<Artist> = groupBy { it.artistId }
    .entries
    .take(20)
    .map { (artistId, tracks) ->
        Artist(
            id = artistId,
            name = tracks.first().artist,
            albumCount = tracks.distinctBy { it.albumId }.size,
            songCount = tracks.size,
            durationMs = tracks.sumOf { it.durationMs },
            artworkUri = tracks.firstNotNullOfOrNull { it.artworkUri },
        )
    }

private fun List<Song>.toGenres(): List<Genre> = mapNotNull { it.genre }
    .groupingBy { it }
    .eachCount()
    .entries
    .take(20)
    .map { (name, count) -> Genre(id = name.hashCode().toLong(), name = name, songCount = count) }
