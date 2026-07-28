package com.neonbeat.core.media.browse

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.common.collect.ImmutableList
import com.neonbeat.core.database.dao.PlaylistDao
import com.neonbeat.core.database.dao.SongDao
import com.neonbeat.core.database.entity.SongEntity
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the hierarchical media tree exposed to external browsers
 * (Android Auto, Wear OS, Assistant, Bluetooth head units).
 *
 * Media3 calls these methods on a binder thread and expects synchronous
 * results, so DAO access is bridged with [runBlocking]; every query is indexed
 * and page-limited, keeping calls in the low-millisecond range.
 */
@Singleton
class BrowseTree @Inject constructor(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
) {
    fun root(): MediaItem = browsable(ROOT, "NeonBeat")

    fun children(parentId: String, page: Int, pageSize: Int): ImmutableList<MediaItem> = when (parentId) {
        ROOT -> ImmutableList.of(
            browsable(SONGS, "Songs"),
            browsable(ALBUMS, "Albums"),
            browsable(ARTISTS, "Artists"),
            browsable(PLAYLISTS, "Playlists"),
            browsable(FAVORITES, "Favorites"),
            browsable(RECENT, "Recently played"),
        )
        SONGS -> runBlocking { songDao.songsByIds(songDao.allSongIds().page(page, pageSize)) }.toItems()
        else -> ImmutableList.of()
    }

    fun item(mediaId: String): MediaItem? {
        val songId = mediaId.toLongOrNull() ?: return null
        return runBlocking { songDao.songById(songId) }?.toMediaItem()
    }

    fun search(query: String, page: Int, pageSize: Int): ImmutableList<MediaItem> =
        runBlocking { songDao.search(sanitize(query), pageSize * (page + 1)) }
            .page(page, pageSize)
            .toItems()

    fun searchCount(query: String): Int = runBlocking { songDao.search(sanitize(query), 200).size }

    private fun <T> List<T>.page(page: Int, pageSize: Int): List<T> =
        drop(page * pageSize).take(pageSize)

    private fun List<SongEntity>.toItems(): ImmutableList<MediaItem> =
        ImmutableList.copyOf(map { it.toMediaItem() })

    private fun browsable(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build(),
        )
        .build()

    companion object {
        const val ROOT = "root"
        const val SONGS = "songs"
        const val ALBUMS = "albums"
        const val ARTISTS = "artists"
        const val PLAYLISTS = "playlists"
        const val FAVORITES = "favorites"
        const val RECENT = "recent"

        /** Escapes FTS operators so user text can never break the MATCH query. */
        fun sanitize(query: String): String = query
            .replace(Regex("[\"*^:()-]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
            .ifBlank { "\"\"" }
    }
}

/** Maps a database row onto the Media3 item consumed by the player and browsers. */
fun SongEntity.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMimeType(mimeType)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(albumArtist ?: artist)
            .setGenre(genre)
            .setComposer(composer)
            .setTrackNumber(trackNumber)
            .setDiscNumber(discNumber)
            .setRecordingYear(year.takeIf { it > 0 })
            .setDurationMs(durationMs)
            .setArtworkUri(artworkUri?.let(android.net.Uri::parse))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build(),
    )
    .build()
