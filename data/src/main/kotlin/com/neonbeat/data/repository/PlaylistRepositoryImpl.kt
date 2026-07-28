package com.neonbeat.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.neonbeat.core.common.di.IoDispatcher
import com.neonbeat.core.database.dao.PlaylistDao
import com.neonbeat.core.database.dao.SongDao
import com.neonbeat.core.database.entity.PlaylistEntity
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playlist storage: manual playlists, smart (rule-based) playlists and M3U I/O.
 *
 * Manual playlists keep an explicit ordered membership table. Smart playlists
 * store only their rules and are compiled to SQL on read, so they stay correct
 * as the library changes without any background reconciliation job.
 */
@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val smartCompiler: SmartPlaylistCompiler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaylistRepository {

    override fun playlists(): Flow<List<Playlist>> =
        playlistDao.playlists()
            .map { rows -> rows.map { it.toPlaylist() } }
            .flowOn(ioDispatcher)

    /**
     * Members of a playlist.
     *
     * Smart playlists ignore the membership table entirely and re-run their
     * compiled query, which is why a smart playlist immediately reflects newly
     * scanned files.
     */
    override fun songsInPlaylist(playlistId: Long): Flow<List<Song>> = combine(
        playlistDao.playlist(playlistId),
        playlistDao.songsInPlaylist(playlistId),
    ) { playlist, rows ->
        val rules = playlist?.smartRules
        if (playlist?.kind == PlaylistKind.SMART.name && !rules.isNullOrBlank()) {
            runSmartQuery(rules)
        } else {
            rows.map { it.toSong() }
        }
    }.flowOn(ioDispatcher)

    override suspend fun create(name: String): Long = withContext(ioDispatcher) {
        insertPlaylist(name = name, kind = PlaylistKind.USER, smartRules = null)
    }

    override suspend fun createSmart(name: String, rules: List<SmartPlaylistRule>): Long =
        withContext(ioDispatcher) {
            insertPlaylist(
                name = name,
                kind = PlaylistKind.SMART,
                smartRules = smartCompiler.encodeRules(rules, matchAll = true, limit = null),
            )
        }

    override suspend fun rename(playlistId: Long, name: String) {
        withContext(ioDispatcher) {
            playlistDao.rename(playlistId, name, System.currentTimeMillis())
        }
    }

    override suspend fun delete(playlistId: Long) {
        withContext(ioDispatcher) { playlistDao.deletePlaylist(playlistId) }
    }

    override suspend fun addSongs(playlistId: Long, songIds: List<Long>) {
        if (songIds.isEmpty()) return
        withContext(ioDispatcher) {
            playlistDao.addSongs(playlistId, songIds, System.currentTimeMillis())
        }
    }

    /**
     * Removes one member and closes the gap it leaves.
     *
     * Positions are rewritten afterwards so drag-and-drop reordering never has
     * to cope with holes in the sequence.
     */
    override suspend fun removeSong(playlistId: Long, songId: Long) {
        withContext(ioDispatcher) {
            playlistDao.removeSong(playlistId, songId)
            val remaining = playlistDao.songsInPlaylistOnce(playlistId).map { it.id }
            playlistDao.reorder(playlistId, remaining, System.currentTimeMillis())
        }
    }

    override suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>) {
        withContext(ioDispatcher) {
            playlistDao.reorder(playlistId, orderedSongIds, System.currentTimeMillis())
        }
    }

    override suspend fun resolveSmart(rules: List<SmartPlaylistRule>): List<Song> =
        withContext(ioDispatcher) {
            val compiled = smartCompiler.compile(rules)
            songDao.rawSongQuery(SimpleSQLiteQuery(compiled.sql, compiled.args.toTypedArray()))
                .map { it.toSong() }
        }

    /**
     * Imports an `.m3u`/`.m3u8` file as a new playlist.
     *
     * Entries are matched by absolute path first and by file name second, so a
     * playlist exported on a desktop still resolves after the files were copied
     * to a different directory on the device. Unmatched entries are skipped
     * rather than failing the whole import.
     */
    override suspend fun importM3u(uri: String): Long = withContext(ioDispatcher) {
        val file = File(uri)
        val entries = M3uCodec.read(file)
        val songIds = entries.mapNotNull { entry -> resolveSongId(entry.path) }
        val playlistId = insertPlaylist(
            name = file.nameWithoutExtension.ifBlank { "Imported playlist" },
            kind = PlaylistKind.USER,
            smartRules = null,
        )
        if (songIds.isNotEmpty()) {
            playlistDao.addSongs(playlistId, songIds, System.currentTimeMillis())
        }
        playlistId
    }

    override suspend fun exportM3u(playlistId: Long, targetUri: String): Boolean =
        withContext(ioDispatcher) {
            runCatching {
                val songs = playlistDao.songsInPlaylistOnce(playlistId).map { it.toSong() }
                val target = File(targetUri)
                target.parentFile?.mkdirs()
                M3uCodec.write(target, songs)
                true
            }.getOrDefault(false)
        }

    private suspend fun insertPlaylist(
        name: String,
        kind: PlaylistKind,
        smartRules: String?,
    ): Long {
        val now = System.currentTimeMillis()
        return playlistDao.createPlaylist(
            PlaylistEntity(
                name = name,
                kind = kind.name,
                smartRules = smartRules,
                artworkUri = null,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    private suspend fun runSmartQuery(rulesJson: String): List<Song> {
        val compiled = smartCompiler.compile(rulesJson)
        return songDao
            .rawSongQuery(SimpleSQLiteQuery(compiled.sql, compiled.args.toTypedArray()))
            .map { it.toSong() }
    }

    private suspend fun resolveSongId(path: String): Long? =
        songDao.findByPath(path)?.id ?: songDao.findByFileName(File(path).name)?.id
}
