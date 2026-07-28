package com.neonbeat.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.neonbeat.core.database.entity.SongEntity
import com.neonbeat.core.database.entity.SongStatsEntity
import com.neonbeat.core.database.model.AlbumAggregate
import com.neonbeat.core.database.model.ArtistAggregate
import com.neonbeat.core.database.model.FolderAggregate
import com.neonbeat.core.database.model.GenreAggregate
import kotlinx.coroutines.flow.Flow

/**
 * All read/write access to the song index.
 *
 * Every list-returning query is exposed as a [PagingSource] so the UI never
 * materialises more than a screenful of rows, which keeps memory flat for very
 * large libraries.
 */
@Dao
interface SongDao {

    @Query(
        """
        SELECT s.* FROM songs s
        LEFT JOIN folders f ON f.path = s.folderPath
        WHERE COALESCE(f.isHidden, 0) = 0 AND COALESCE(f.hasNoMedia, 0) = 0
        ORDER BY s.titleKey COLLATE NOCASE ASC
        """,
    )
    fun pagingSongsByTitle(): PagingSource<Int, SongEntity>

    @Query(
        """
        SELECT s.* FROM songs s
        LEFT JOIN folders f ON f.path = s.folderPath
        WHERE COALESCE(f.isHidden, 0) = 0 AND COALESCE(f.hasNoMedia, 0) = 0
        ORDER BY s.dateAddedSeconds DESC
        """,
    )
    fun pagingRecentlyAdded(): PagingSource<Int, SongEntity>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN song_stats st ON st.songId = s.id
        WHERE st.lastPlayedAtEpochMs > 0
        ORDER BY st.lastPlayedAtEpochMs DESC
        """,
    )
    fun pagingRecentlyPlayed(): PagingSource<Int, SongEntity>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN song_stats st ON st.songId = s.id
        WHERE st.playCount > 0
        ORDER BY st.playCount DESC, st.lastPlayedAtEpochMs DESC
        """,
    )
    fun pagingMostPlayed(): PagingSource<Int, SongEntity>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN song_stats st ON st.songId = s.id
        WHERE st.isFavorite = 1
        ORDER BY st.lastPlayedAtEpochMs DESC
        """,
    )
    fun pagingFavorites(): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC")
    fun songsInAlbum(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artistId = :artistId ORDER BY year DESC, album ASC, trackNumber ASC")
    fun songsByArtist(artistId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY titleKey COLLATE NOCASE ASC")
    fun songsInGenre(genre: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE folderPath = :path ORDER BY titleKey COLLATE NOCASE ASC")
    fun songsInFolder(path: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun songById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun songsByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT id FROM songs ORDER BY titleKey COLLATE NOCASE ASC")
    suspend fun allSongIds(): List<Long>

    @Query("SELECT COUNT(*) FROM songs")
    fun songCount(): Flow<Int>

    // ---------------------------------------------------------------- search

    /**
     * FTS4 prefix search. The caller passes an already-sanitised MATCH term such
     * as `stairway*`; see `SearchQuerySanitizer` in the data module.
     */
    @Query(
        """
        SELECT s.* FROM songs s
        JOIN songs_fts fts ON fts.rowid = s.rowid
        WHERE songs_fts MATCH :match
        LIMIT :limit
        """,
    )
    suspend fun search(match: String, limit: Int = 50): List<SongEntity>

    // ----------------------------------------------------------- aggregates

    @Query(
        """
        SELECT albumId AS id, album AS title, artist, artistId, MAX(year) AS year,
               COUNT(*) AS songCount, SUM(durationMs) AS durationMs,
               MAX(artworkUri) AS artworkUri, MAX(dateAddedSeconds) AS dateAddedSeconds
        FROM songs GROUP BY albumId ORDER BY album COLLATE NOCASE ASC
        """,
    )
    fun pagingAlbums(): PagingSource<Int, AlbumAggregate>

    @Query(
        """
        SELECT artistId AS id, artist AS name, COUNT(DISTINCT albumId) AS albumCount,
               COUNT(*) AS songCount, SUM(durationMs) AS durationMs, MAX(artworkUri) AS artworkUri
        FROM songs GROUP BY artistId ORDER BY artist COLLATE NOCASE ASC
        """,
    )
    fun pagingArtists(): PagingSource<Int, ArtistAggregate>

    @Query(
        """
        SELECT genre AS name, COUNT(*) AS songCount FROM songs
        WHERE genre IS NOT NULL AND genre != '' GROUP BY genre ORDER BY genre COLLATE NOCASE ASC
        """,
    )
    fun genres(): Flow<List<GenreAggregate>>

    @Query(
        """
        SELECT s.folderPath AS path, COUNT(*) AS songCount,
               COALESCE(f.isHidden, 0) AS isHidden, COALESCE(f.hasNoMedia, 0) AS hasNoMedia
        FROM songs s LEFT JOIN folders f ON f.path = s.folderPath
        GROUP BY s.folderPath ORDER BY s.folderPath COLLATE NOCASE ASC
        """,
    )
    fun folders(): Flow<List<FolderAggregate>>

    /** Duplicate finder: same title+artist+duration bucket, more than one file. */
    @Query(
        """
        SELECT * FROM songs WHERE id IN (
            SELECT s.id FROM songs s
            JOIN (
                SELECT titleKey, artist, durationMs / 1000 AS secs
                FROM songs GROUP BY titleKey, artist, secs HAVING COUNT(*) > 1
            ) d ON d.titleKey = s.titleKey AND d.artist = s.artist AND d.secs = s.durationMs / 1000
        )
        ORDER BY titleKey COLLATE NOCASE ASC, sizeBytes DESC
        """,
    )
    suspend fun findDuplicates(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE artworkUri IS NULL ORDER BY album COLLATE NOCASE ASC")
    suspend fun findMissingArtwork(): List<SongEntity>

    /** Escape hatch used by the smart-playlist rule compiler. */
    @RawQuery(observedEntities = [SongEntity::class, SongStatsEntity::class])
    suspend fun rawSongQuery(query: SupportSQLiteQuery): List<SongEntity>

    // ------------------------------------------------------------- mutations

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStatsIfMissing(stats: List<SongStatsEntity>)

    @Query("DELETE FROM songs WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissing(keepIds: List<Long>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Applies one scan batch atomically so readers never observe a half state. */
    @Transaction
    suspend fun applyScanBatch(songs: List<SongEntity>) {
        upsertAll(songs)
        insertStatsIfMissing(songs.map { SongStatsEntity(songId = it.id) })
    }
}
