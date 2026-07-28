package com.neonbeat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.neonbeat.core.database.entity.BookmarkEntity
import com.neonbeat.core.database.entity.FolderEntity
import com.neonbeat.core.database.entity.LyricsEntity
import com.neonbeat.core.database.entity.PlayHistoryEntity
import com.neonbeat.core.database.entity.PlaylistEntity
import com.neonbeat.core.database.entity.PlaylistSongEntity
import com.neonbeat.core.database.entity.QueueItemEntity
import com.neonbeat.core.database.entity.SongEntity
import com.neonbeat.core.database.entity.SongStatsEntity
import com.neonbeat.core.database.model.ArtistPlayCount
import com.neonbeat.core.database.model.PlaylistAggregate
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id, p.name, p.kind, p.smartRules, p.artworkUri, p.createdAtEpochMs, p.updatedAtEpochMs,
               COUNT(ps.songId) AS songCount, COALESCE(SUM(s.durationMs), 0) AS durationMs
        FROM playlists p
        LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
        LEFT JOIN songs s ON s.id = ps.songId
        GROUP BY p.id ORDER BY p.updatedAtEpochMs DESC
        """,
    )
    fun playlists(): Flow<List<PlaylistAggregate>>

    @Query(
        """
        SELECT s.* FROM playlist_songs ps
        JOIN songs s ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId ORDER BY ps.position ASC
        """,
    )
    fun songsInPlaylist(playlistId: Long): Flow<List<SongEntity>>

    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY updatedAtEpochMs DESC")
    suspend fun allPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query(
        """
        SELECT s.* FROM playlist_songs ps
        JOIN songs s ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId ORDER BY ps.position ASC
        """,
    )
    suspend fun songsInPlaylistOnce(playlistId: Long): List<SongEntity>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("UPDATE playlists SET name = :name, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun lastPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    /** Appends songs, keeping positions contiguous. */
    @Transaction
    suspend fun addSongs(playlistId: Long, songIds: List<Long>, now: Long, addedBy: String? = null) {
        val start = lastPosition(playlistId) + 1
        insertEntries(
            songIds.mapIndexed { index, songId ->
                PlaylistSongEntity(playlistId, songId, start + index, now, addedBy)
            },
        )
        touch(playlistId, now)
    }

    /** Rewrites the whole ordering after a drag-and-drop reorder. */
    @Transaction
    suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>, now: Long) {
        clearPlaylist(playlistId)
        insertEntries(
            orderedSongIds.mapIndexed { index, songId ->
                PlaylistSongEntity(playlistId, songId, index, now)
            },
        )
        touch(playlistId, now)
    }

    @Query("UPDATE playlists SET updatedAtEpochMs = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)
}

@Dao
interface StatsDao {

    @Query("SELECT * FROM song_stats WHERE songId = :songId")
    fun stats(songId: Long): Flow<SongStatsEntity?>

    @Query("SELECT isFavorite FROM song_stats WHERE songId = :songId")
    fun isFavorite(songId: Long): Flow<Boolean?>

    @Upsert
    suspend fun upsert(stats: SongStatsEntity)

    @Query("UPDATE song_stats SET isFavorite = NOT isFavorite WHERE songId = :songId")
    suspend fun toggleFavorite(songId: Long)

    @Query("UPDATE song_stats SET isFavorite = :favorite WHERE songId = :songId")
    suspend fun setFavorite(songId: Long, favorite: Boolean)

    /** Restores counters from a backup without inventing history rows. */
    @Query(
        """
        UPDATE song_stats SET playCount = :playCount, lastPlayedAtEpochMs = :lastPlayedAt
        WHERE songId = :songId
        """,
    )
    suspend fun restorePlayCount(songId: Long, playCount: Int, lastPlayedAt: Long)

    @Query(
        """
        SELECT s.artist AS name, SUM(st.playCount) AS playCount
        FROM songs s JOIN song_stats st ON st.songId = s.id
        WHERE st.playCount > 0
        GROUP BY s.artist ORDER BY playCount DESC LIMIT :limit
        """,
    )
    fun topArtists(limit: Int): Flow<List<ArtistPlayCount>>

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()

    @Query(
        """
        UPDATE song_stats
        SET playCount = playCount + 1,
            lastPlayedAtEpochMs = :now,
            totalListenedMs = totalListenedMs + :listenedMs
        WHERE songId = :songId
        """,
    )
    suspend fun recordPlay(songId: Long, now: Long, listenedMs: Long)

    @Query("UPDATE song_stats SET skipCount = skipCount + 1 WHERE songId = :songId")
    suspend fun recordSkip(songId: Long)

    @Insert
    suspend fun insertHistory(event: PlayHistoryEntity)

    @Query("SELECT * FROM play_history ORDER BY playedAtEpochMs DESC LIMIT :limit")
    fun history(limit: Int): Flow<List<PlayHistoryEntity>>

    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM play_history WHERE playedAtEpochMs >= :sinceEpochMs")
    fun listenedMsSince(sinceEpochMs: Long): Flow<Long>

    @Query(
        """
        SELECT s.* FROM songs s JOIN song_stats st ON st.songId = s.id
        WHERE s.genre IN (
            SELECT s2.genre FROM songs s2 JOIN song_stats st2 ON st2.songId = s2.id
            WHERE st2.playCount > 0 GROUP BY s2.genre ORDER BY SUM(st2.playCount) DESC LIMIT 3
        )
        AND st.playCount = 0
        ORDER BY RANDOM() LIMIT :limit
        """,
    )
    suspend fun recommendations(limit: Int): List<SongEntity>
}

@Dao
interface QueueDao {

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun queue(): List<QueueItemEntity>

    @Query("DELETE FROM queue_items")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<QueueItemEntity>)

    @Transaction
    suspend fun replace(items: List<QueueItemEntity>) {
        clear()
        insert(items)
    }
}

@Dao
interface FolderDao {
    @Upsert
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Query("UPDATE folders SET isHidden = :hidden WHERE path = :path")
    suspend fun setHidden(path: String, hidden: Boolean)

    @Query("SELECT * FROM folders WHERE isHidden = 1")
    fun hiddenFolders(): Flow<List<FolderEntity>>
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    fun lyrics(songId: Long): Flow<LyricsEntity?>

    @Upsert
    suspend fun upsert(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE songId = :songId")
    suspend fun delete(songId: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE songId = :songId ORDER BY positionMs ASC")
    fun bookmarks(songId: Long): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun add(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}
