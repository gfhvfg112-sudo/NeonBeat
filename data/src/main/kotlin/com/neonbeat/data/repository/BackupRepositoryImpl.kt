package com.neonbeat.data.repository

import android.content.Context
import com.neonbeat.core.common.di.IoDispatcher
import com.neonbeat.core.database.dao.PlaylistDao
import com.neonbeat.core.database.dao.SongDao
import com.neonbeat.core.database.dao.StatsDao
import com.neonbeat.core.datastore.SettingsRepository
import com.neonbeat.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup and restore of user-created data.
 *
 * Only data the user cannot regenerate is backed up: settings, playlists,
 * favourites, play counts and bookmarks. The scanned library itself is
 * deliberately excluded — it is rebuilt from MediaStore in seconds and would
 * otherwise make the backup file enormous on a 100k-song device.
 *
 * Restores match songs by absolute path, then by file name, so a backup still
 * applies after files have been reorganised or moved to another device.
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val statsDao: StatsDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackupRepository {

    override suspend fun exportBackup(targetPath: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            val playlists = JSONArray()
            playlistDao.allPlaylists().forEach { playlist ->
                val songs = JSONArray()
                playlistDao.songsInPlaylist(playlist.id).forEach { songs.put(it.data) }
                playlists.put(
                    JSONObject()
                        .put("name", playlist.name)
                        .put("kind", playlist.kind)
                        .put("rules", playlist.rulesJson ?: JSONObject.NULL)
                        .put("songs", songs),
                )
            }

            val favorites = JSONArray()
            songDao.allFavoritePaths().forEach { favorites.put(it) }

            val playCounts = JSONArray()
            songDao.allPlayCounts().forEach { row ->
                playCounts.put(
                    JSONObject()
                        .put("path", row.path)
                        .put("playCount", row.playCount)
                        .put("lastPlayedAt", row.lastPlayedAt ?: JSONObject.NULL),
                )
            }

            val root = JSONObject()
                .put("version", BACKUP_VERSION)
                .put("createdAt", System.currentTimeMillis())
                .put("settings", JSONObject(settingsRepository.exportJson()))
                .put("playlists", playlists)
                .put("favorites", favorites)
                .put("playCounts", playCounts)

            File(targetPath).apply {
                parentFile?.mkdirs()
                writeText(root.toString(2))
            }
            true
        }.getOrDefault(false)
    }

    override suspend fun importBackup(sourcePath: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            val root = JSONObject(File(sourcePath).readText())
            require(root.optInt("version") <= BACKUP_VERSION) { "Backup is from a newer version" }

            root.optJSONObject("settings")?.let { settingsRepository.importJson(it.toString()) }

            val favorites = root.optJSONArray("favorites") ?: JSONArray()
            for (index in 0 until favorites.length()) {
                resolveSongId(favorites.getString(index))?.let { songDao.setFavorite(it, true) }
            }

            val playCounts = root.optJSONArray("playCounts") ?: JSONArray()
            for (index in 0 until playCounts.length()) {
                val item = playCounts.getJSONObject(index)
                resolveSongId(item.getString("path"))?.let { songId ->
                    statsDao.restorePlayCount(
                        songId = songId,
                        playCount = item.optInt("playCount"),
                        lastPlayedAt = item.optLong("lastPlayedAt").takeIf { it > 0 },
                    )
                }
            }

            val playlists = root.optJSONArray("playlists") ?: JSONArray()
            for (index in 0 until playlists.length()) {
                val item = playlists.getJSONObject(index)
                val songs = item.optJSONArray("songs") ?: JSONArray()
                val ids = buildList {
                    for (songIndex in 0 until songs.length()) {
                        resolveSongId(songs.getString(songIndex))?.let { add(it) }
                    }
                }
                playlistDao.restorePlaylist(
                    name = item.getString("name"),
                    kind = item.optString("kind"),
                    rulesJson = item.optString("rules").takeIf { it.isNotBlank() && it != "null" },
                    songIds = ids,
                )
            }
            true
        }.getOrDefault(false)
    }

    override suspend fun exportSettings(targetPath: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            File(targetPath).apply {
                parentFile?.mkdirs()
                writeText(settingsRepository.exportJson())
            }
            true
        }.getOrDefault(false)
    }

    override suspend fun importSettings(sourcePath: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            settingsRepository.importJson(File(sourcePath).readText())
            true
        }.getOrDefault(false)
    }

    /** Path first, then file name, so moved libraries still restore cleanly. */
    private suspend fun resolveSongId(path: String): Long? =
        songDao.findByPath(path)?.id ?: songDao.findByFileName(File(path).name)?.id

    private companion object {
        const val BACKUP_VERSION = 1
    }
}
