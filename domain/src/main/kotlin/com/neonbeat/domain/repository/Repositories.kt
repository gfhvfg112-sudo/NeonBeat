package com.neonbeat.domain.repository

import androidx.paging.PagingData
import com.neonbeat.core.model.Album
import com.neonbeat.core.model.Artist
import com.neonbeat.core.model.Bookmark
import com.neonbeat.core.model.Genre
import com.neonbeat.core.model.Lyrics
import com.neonbeat.core.model.MusicFolder
import com.neonbeat.core.model.Playlist
import com.neonbeat.core.model.SmartPlaylistRule
import com.neonbeat.core.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the local music library.
 *
 * Implementations live in the `:data` module; the domain layer only ever sees
 * these interfaces, which keeps use cases trivially testable with fakes.
 */
interface MusicRepository {
    fun songs(): Flow<PagingData<Song>>
    fun recentlyAdded(): Flow<PagingData<Song>>
    fun recentlyPlayed(): Flow<PagingData<Song>>
    fun mostPlayed(): Flow<PagingData<Song>>
    fun favorites(): Flow<PagingData<Song>>
    fun albums(): Flow<PagingData<Album>>
    fun artists(): Flow<PagingData<Artist>>
    fun genres(): Flow<List<Genre>>
    fun folders(): Flow<List<MusicFolder>>
    fun songsInAlbum(albumId: Long): Flow<List<Song>>
    fun songsByArtist(artistId: Long): Flow<List<Song>>
    fun songsInGenre(genre: String): Flow<List<Song>>
    fun songsInFolder(path: String): Flow<List<Song>>
    fun songCount(): Flow<Int>

    suspend fun songById(id: Long): Song?
    suspend fun songsByIds(ids: List<Long>): List<Song>
    suspend fun allSongIds(): List<Long>

    /** Incrementally reconciles the index with MediaStore. */
    suspend fun rescan(force: Boolean = false): ScanResult

    suspend fun setFolderHidden(path: String, hidden: Boolean)
    suspend fun toggleFavorite(songId: Long)
    fun isFavorite(songId: Long): Flow<Boolean>

    // Batch library maintenance
    suspend fun deleteSongs(songIds: List<Long>): Int
    suspend fun renameFile(songId: Long, newName: String): Boolean
    suspend fun moveFiles(songIds: List<Long>, targetFolder: String): Int
    suspend fun updateTags(songId: Long, tags: Map<String, String>): Boolean
    suspend fun findDuplicates(): List<List<Song>>
    suspend fun findMissingArtwork(): List<Song>
}

/** Outcome of a library scan, surfaced in the UI as a snackbar. */
data class ScanResult(
    val added: Int,
    val updated: Int,
    val removed: Int,
    val durationMs: Long,
)

interface PlaylistRepository {
    fun playlists(): Flow<List<Playlist>>
    fun songsInPlaylist(playlistId: Long): Flow<List<Song>>
    suspend fun create(name: String): Long
    suspend fun createSmart(name: String, rules: List<SmartPlaylistRule>): Long
    suspend fun rename(playlistId: Long, name: String)
    suspend fun delete(playlistId: Long)
    suspend fun addSongs(playlistId: Long, songIds: List<Long>)
    suspend fun removeSong(playlistId: Long, songId: Long)
    suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>)
    /** Resolves a smart playlist's rules against the current library. */
    suspend fun resolveSmart(rules: List<SmartPlaylistRule>): List<Song>
    suspend fun importM3u(uri: String): Long
    suspend fun exportM3u(playlistId: Long, targetUri: String): Boolean
}

interface SearchRepository {
    /** Instant, debounced multi-entity search backed by FTS4. */
    fun search(query: String): Flow<SearchResults>

    /** Most recent non-trivial queries, newest first. */
    fun recentQueries(): Flow<List<String>>

    suspend fun clearRecentQueries()
}

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val folders: List<MusicFolder> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() &&
            genres.isEmpty() && folders.isEmpty() && playlists.isEmpty()
}

interface LyricsRepository {
    /** Embedded tag -> sidecar `.lrc` -> cached download, in that order. */
    fun lyrics(song: Song): Flow<Lyrics?>
    suspend fun downloadLyrics(song: Song): Lyrics?
    suspend fun saveManual(songId: Long, content: String)
}

interface StatsRepository {
    fun listeningMinutes(sinceEpochMs: Long): Flow<Long>
    fun topArtists(limit: Int): Flow<List<Pair<String, Int>>>
    fun history(limit: Int): Flow<List<Song>>
    suspend fun recommendations(limit: Int): List<Song>
}

interface BookmarkRepository {
    fun bookmarks(songId: Long): Flow<List<Bookmark>>
    suspend fun add(songId: Long, positionMs: Long, label: String)
    suspend fun delete(id: Long)
}

/** Backup and restore of the database plus every preference. */
interface BackupRepository {
    suspend fun exportBackup(targetPath: String): Boolean
    suspend fun importBackup(sourcePath: String): Boolean
    suspend fun exportSettings(targetPath: String): Boolean
    suspend fun importSettings(sourcePath: String): Boolean
}
