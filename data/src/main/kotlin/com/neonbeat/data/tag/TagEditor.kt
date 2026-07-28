package com.neonbeat.data.tag

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.neonbeat.core.database.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-level library maintenance: tag writes, renames, moves and deletes.
 *
 * On Android 11+ the app cannot write arbitrary media files directly, so every
 * mutation goes through MediaStore. When the system denies a write it returns a
 * `RecoverableSecurityException`; the caller is expected to surface the
 * resulting [android.app.PendingIntent] to the user via
 * `MediaStore.createWriteRequest`, which is what [buildWriteRequest] prepares.
 */
@Singleton
class TagEditor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver get() = context.contentResolver

    /**
     * Writes MediaStore-visible tags.
     *
     * @param tags Keys map to `MediaStore.Audio.Media` columns: `title`,
     *   `artist`, `album`, `album_artist`, `composer`, `genre`, `year`, `track`.
     */
    fun writeTags(song: SongEntity, tags: Map<String, String>): Boolean = runCatching {
        val values = ContentValues().apply {
            tags["title"]?.let { put(MediaStore.Audio.Media.TITLE, it) }
            tags["artist"]?.let { put(MediaStore.Audio.Media.ARTIST, it) }
            tags["album"]?.let { put(MediaStore.Audio.Media.ALBUM, it) }
            tags["album_artist"]?.let { put(MediaStore.Audio.Media.ALBUM_ARTIST, it) }
            tags["composer"]?.let { put(MediaStore.Audio.Media.COMPOSER, it) }
            tags["genre"]?.let { put(MediaStore.Audio.Media.GENRE, it) }
            tags["year"]?.toIntOrNull()?.let { put(MediaStore.Audio.Media.YEAR, it) }
            tags["track"]?.toIntOrNull()?.let { put(MediaStore.Audio.Media.TRACK, it) }
        }
        resolver.update(Uri.parse(song.uri), values, null, null) > 0
    }.getOrDefault(false)

    /** Renames the underlying file, keeping the original extension. */
    fun rename(song: SongEntity, newName: String): Boolean = runCatching {
        val extension = File(song.data).extension
        val safeName = newName.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeName.$extension")
        }
        resolver.update(Uri.parse(song.uri), values, null, null) > 0
    }.getOrDefault(false)

    /**
     * Moves files by updating `RELATIVE_PATH`.
     *
     * @param targetFolder Path relative to the shared storage root, e.g. `Music/Sorted`.
     * @return Number of files successfully moved.
     */
    fun move(songs: List<SongEntity>, targetFolder: String): Int = songs.count { song ->
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.RELATIVE_PATH, targetFolder.trimEnd('/') + "/")
            }
            resolver.update(Uri.parse(song.uri), values, null, null) > 0
        }.getOrDefault(false)
    }

    /** Deletes files from storage; returns the number actually removed. */
    fun deleteFiles(songs: List<SongEntity>): Int = songs.count { song ->
        runCatching { resolver.delete(Uri.parse(song.uri), null, null) > 0 }.getOrDefault(false)
    }

    /**
     * Builds a system consent dialog for writing to files the app does not own.
     * Required on API 30+ before any batch edit or delete.
     */
    fun buildWriteRequest(songs: List<SongEntity>) =
        MediaStore.createWriteRequest(resolver, songs.map { Uri.parse(it.uri) })

    fun buildDeleteRequest(songs: List<SongEntity>) =
        MediaStore.createDeleteRequest(resolver, songs.map { Uri.parse(it.uri) })
}
