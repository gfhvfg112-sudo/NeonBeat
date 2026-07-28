package com.neonbeat.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The canonical song index.
 *
 * Indices are chosen for the exact sort orders exposed in the library UI so
 * that Paging 3 queries stay on covering indices even at 100k+ rows.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index("title"),
        Index("artistId"),
        Index("albumId"),
        Index("folderPath"),
        Index("dateAddedSeconds"),
        Index(value = ["data"], unique = true),
    ],
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val titleKey: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val albumArtist: String?,
    val genre: String?,
    val composer: String?,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    val mimeType: String,
    val uri: String,
    val data: String,
    val folderPath: String,
    val dateAddedSeconds: Long,
    val dateModifiedSeconds: Long,
    val replayGainTrack: Float?,
    val replayGainAlbum: Float?,
    val hasEmbeddedLyrics: Boolean,
    val artworkUri: String?,
)

/** Full-text mirror of [SongEntity] powering instant search. */
@Fts4(contentEntity = SongEntity::class)
@Entity(tableName = "songs_fts")
data class SongFtsEntity(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val genre: String?,
    val folderPath: String,
)

/** Per-song mutable user state kept separate so re-scans never clobber it. */
@Entity(
    tableName = "song_stats",
    indices = [Index("playCount"), Index("lastPlayedAtEpochMs"), Index("isFavorite")],
)
data class SongStatsEntity(
    @PrimaryKey val songId: Long,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayedAtEpochMs: Long = 0L,
    val totalListenedMs: Long = 0L,
    val isFavorite: Boolean = false,
    val rating: Int = 0,
)

@Entity(tableName = "play_history", indices = [Index("songId"), Index("playedAtEpochMs")])
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAtEpochMs: Long,
    val listenedMs: Long,
    val completed: Boolean,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val smartRules: String?,
    val artworkUri: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "position"],
    indices = [Index("songId")],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val addedAtEpochMs: Long,
    /** Free-form identifier of who added the entry, for collaborative playlists. */
    val addedBy: String? = null,
)

/** Persisted playback queue so the app restores exactly where the user left off. */
@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val songId: Long,
    val shuffledPosition: Int,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val path: String,
    val name: String,
    val isHidden: Boolean = false,
    val hasNoMedia: Boolean = false,
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    /** LRC-formatted text, synced or plain. */
    val content: String,
    val source: String,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "bookmarks", indices = [Index("songId")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val positionMs: Long,
    val label: String,
)
