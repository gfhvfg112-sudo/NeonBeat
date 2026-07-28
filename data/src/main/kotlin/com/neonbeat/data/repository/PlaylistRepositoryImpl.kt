package com.neonbeat.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.neonbeat.core.common.di.IoDispatcher
import com.neonbeat.core.database.dao.PlaylistDao
import com.neonbeat.core.database.dao.SongDao
import com.neonbeat.core.database.entity.PlaylistEntity
import com.neonbeat.core.database.entity.PlaylistSongCrossRef
import com.neonbeat.core.model.Playlist
import com.neonbeat.core.model.PlaylistKind
import com.neonbeat.core.model.SmartPlaylistRule
import com.neonbeat.core.model.Song
import com.neonbeat.data.mapper.toPlaylist
import com.neonbeat.data.mapper.toSong
import com.neonbeat.data.playlist.M3uCodec
import com.neonbeat.data.playlist.SmartPlaylistCompiler
import com.neonbeat.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playlist storage: manual playlists, smart (rule-based) playlists and M3U I/O.
 *
 * Manual playlists store an explicit ordered membership table; smart playlists
 * store only their rules and are compiled to SQL on read, so they stay correct
 * as the library changes without any background reconciliation.
 */
@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val smartCompiler: SmartPlaylistCompiler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaylistRepository {

    override fun playlists(): Flow<List<Playlist>> =
        playlistDao.observePlaylists().map { list -> list.map { it.toPlaylist() } }

    override suspend fun createPlaylist(name: String): Long = withContext(ioDispatcher) {
        playlistDao.insert(
            PlaylistEntity(
                name = name,
                kind = PlaylistKind.MANUAL.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun createSmartPlaylist(
        name: String,
        rules: List<SmartPlaylistRule>,
        matchAll: Boolean,
        limit: Int?,
    ): Long = withContext(ioDispatcher) {
        playlistDao.insert(
            PlaylistEntity(
                name = name,
                kind = PlaylistKind.SMART.name,
                rulesJson = smartCompiler.encodeRules(rules, matchAll, limit),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) = withContext(ioDispatcher) {
        playlistDao.rename(playlistId, name, System.currentTimeMillis())
    }

    override suspend fun deletePlaylist(playlistId: Long) = withContext(ioDispatcher) {
        playlistDao.deleteById(playlistId)
    }

    /**
     * Appends songs, preserving the caller's order.
     *
     * Positions continue from the current maximum rather than being recomputed,
     * which keeps the insert O(n) in the number of added songs instead of the
     * playlist length.
     */
    override suspend fun addSongs(playlistId: Long, songIds: List<Long>) = withContext(ioDispatcher) {
        val start = playlistDao.maxPosition(playlistId) + 1
        playlistDao.insertCrossRefs(
            songIds.mapIndexed { index, songId ->
                PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = songId,
                    position = start + index,
                    addedAt = System.currentTimeMillis(),
                )
            },
        )
        playlistDao.touch(playlistId, System.currentTimeMillis())
    }

    override suspend fun removeSong(playlistId: Long, songId: Long) = withContext(ioDispatcher) {
        playlistDao.removeCrossRef(playlistId, songId)
        playlistDao.touch(playlistId, System.currentTimeMillis())
    }

    /** Reorders after a drag-and-drop; rewrites only the affected span. */
    override suspend fun moveSong(playlistId: Long, from: Int, to: Int) = withContext(ioDispatcher) {
        val refs = playlistDao.crossRefs(playlistId).toMutableList()
        if (from !in refs.indices || to !in refs.indices) return@withContext
        val moved = refs.removeAt(from)
        refs.add(to, moved)
        playlistDao.insertCrossRefs(
            refs.mapIndexed { index, ref -> ref.copy(position = index) },
        )
        playlistDao.touch(playlistId, System.currentTimeMillis())
    }

    /**
     * Returns the playlist contents.
     *
     * Manual playlists read their membership table; smart playlists are compiled
     * to a parameterised query and executed against the songs table on demand.
     */
    override fun songsInPlaylist(playlistId: Long): Flow<List<Song>> =
        playlistDao.observePlaylistWithKind(playlistId).map { playlist ->
            when {
                playlist == null -> emptyList()
                playlist.kind == PlaylistKind.SMART.name && playlist.rulesJson != null -> {
                    val query = smartCompiler.compile(playlist.rulesJson)
                    songDao.rawSongQuery(SimpleSQLiteQuery(query.sql, query.args.toTypedArray()))
                        .map { it.toSong() }
                }
                else -> playlistDao.songsInPlaylist(playlistId).map { it.toSong() }
            }
        }

    // ------------------------------------------------------------------ M3U

    /**
     * Imports an M3U/M3U8 file.
     *
     * Entries are matched to the library by absolute path first, then by file
     * name, so playlists exported on another device still resolve. Unmatched
     * entries are skipped rather than failing the whole import.
     */
    override suspend fun importM3u(fileUri: String, name: String): Long = withContext(ioDispatcher) {
        val entries = M3uCodec.read(File(fileUri))
        val playlistId = createPlaylist(name)
        val resolved = entries.mapNotNull { entry ->
            songDao.findByPath(entry.path)?.id ?: songDao.findByFileName(File(entry.path).name)?.id
        }
        if (resolved.isNotEmpty()) addSongs(playlistId, resolved)
        playlistId
    }

    override suspend fun exportM3u(playlistId: Long, targetPath: String): Boolean =
        withContext(ioDispatcher) {
            runCatching {
                val songs = playlistDao.songsInPlaylist(playlistId).map { it.toSong() }
                M3uCodec.write(File(targetPath), songs)
                true
            }.getOrDefault(false)
        }
}
