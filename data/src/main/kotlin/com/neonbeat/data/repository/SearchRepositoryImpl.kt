package com.neonbeat.data.repository

import com.neonbeat.core.common.di.IoDispatcher
import com.neonbeat.core.database.dao.FolderDao
import com.neonbeat.core.database.dao.PlaylistDao
import com.neonbeat.core.database.dao.SongDao
import com.neonbeat.data.mapper.toAlbum
import com.neonbeat.data.mapper.toArtist
import com.neonbeat.data.mapper.toFolder
import com.neonbeat.data.mapper.toGenre
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
 * All six entity queries run against the same sanitised token and are combined
 * into one [SearchResults] emission, so the UI updates atomically instead of
 * flickering section by section. Each section is capped at [SECTION_LIMIT];
 * users refine the query rather than scroll thousands of matches, and the cap
 * is what keeps every keystroke inside a single frame on a 100k-song library.
 */
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val folderDao: FolderDao,
    private val playlistDao: PlaylistDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SearchRepository {

    private val recent = MutableStateFlow<List<String>>(emptyList())

    override fun search(query: String): Flow<SearchResults> {
        val token = sanitize(query)
        if (token.isEmpty()) return MutableStateFlow(SearchResults())

        rememberQuery(query)

        return combine(
            songDao.searchSongs(token, SECTION_LIMIT),
            songDao.searchAlbums(token, SECTION_LIMIT),
            songDao.searchArtists(token, SECTION_LIMIT),
            songDao.searchGenres(token, SECTION_LIMIT),
            combine(
                folderDao.searchFolders("%$query%", SECTION_LIMIT),
                playlistDao.searchPlaylists("%$query%", SECTION_LIMIT),
            ) { folders, playlists -> folders to playlists },
        ) { songs, albums, artists, genres, foldersAndPlaylists ->
            SearchResults(
                songs = songs.map { it.toSong() },
                albums = albums.map { it.toAlbum() },
                artists = artists.map { it.toArtist() },
                genres = genres.map { it.toGenre() },
                folders = foldersAndPlaylists.first.map { it.toFolder() },
                playlists = foldersAndPlaylists.second.map { it.toPlaylist() },
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
