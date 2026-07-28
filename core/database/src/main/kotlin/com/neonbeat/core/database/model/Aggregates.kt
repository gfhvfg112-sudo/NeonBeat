package com.neonbeat.core.database.model

/**
 * Projection rows returned by GROUP BY queries in [com.neonbeat.core.database.dao.SongDao].
 *
 * These are deliberately not Room entities: albums, artists, genres and folders
 * are derived views over the song index, so there is nothing to keep in sync.
 */
data class AlbumAggregate(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val year: Int,
    val songCount: Int,
    val durationMs: Long,
    val artworkUri: String?,
    val dateAddedSeconds: Long,
)

data class ArtistAggregate(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val durationMs: Long,
    val artworkUri: String?,
)

data class GenreAggregate(
    val name: String,
    val songCount: Int,
)

data class FolderAggregate(
    val path: String,
    val songCount: Int,
    val isHidden: Boolean,
    val hasNoMedia: Boolean,
)

data class PlaylistAggregate(
    val id: Long,
    val name: String,
    val kind: String,
    val smartRules: String?,
    val artworkUri: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val songCount: Int,
    val durationMs: Long,
)
