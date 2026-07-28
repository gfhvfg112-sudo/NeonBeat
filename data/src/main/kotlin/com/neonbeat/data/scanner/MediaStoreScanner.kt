package com.neonbeat.data.scanner

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.neonbeat.core.database.entity.SongEntity
import com.neonbeat.core.model.AudioFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams the device audio index out of MediaStore.
 *
 * Performance notes for very large libraries:
 * - A single cursor pass with an explicit projection avoids per-row IPC.
 * - Rows are emitted in batches so the caller can write to Room incrementally
 *   and the UI can show progress; peak memory stays near one batch.
 * - Column indices are resolved once, outside the loop.
 * - No `MediaMetadataRetriever` is used during the scan; expensive tag reads
 *   (ReplayGain, embedded lyrics) are deferred to a background enrichment pass.
 */
@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** One page of scanned rows plus running progress information. */
    data class Batch(val songs: List<SongEntity>, val scanned: Int, val total: Int)

    /**
     * Performs a full scan.
     *
     * @param minDurationSeconds Tracks shorter than this are skipped (ringtones, notification blips).
     * @param onBatch Invoked for every [BATCH_SIZE] rows; return quickly.
     */
    suspend fun scan(
        minDurationSeconds: Int = 5,
        batchSize: Int = BATCH_SIZE,
        onBatch: suspend (Batch) -> Unit,
    ) {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val mimeArgs = AudioFormat.supportedMimeTypes
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND ${MediaStore.Audio.Media.DURATION} >= ?")
            append(" AND ${MediaStore.Audio.Media.MIME_TYPE} IN (")
            append(mimeArgs.joinToString(",") { "?" })
            append(")")
        }
        val args = buildList {
            add((minDurationSeconds * 1000).toString())
            addAll(mimeArgs)
        }.toTypedArray()

        context.contentResolver.query(
            collection,
            PROJECTION,
            selection,
            args,
            "${MediaStore.Audio.Media.TITLE_KEY} ASC",
        )?.use { cursor ->
            val total = cursor.count
            val columns = Columns(cursor)
            val buffer = ArrayList<SongEntity>(batchSize)
            var scanned = 0

            while (cursor.moveToNext()) {
                val song = cursor.readSong(columns) ?: continue
                buffer += song
                scanned++
                if (buffer.size >= batchSize) {
                    onBatch(Batch(buffer.toList(), scanned, total))
                    buffer.clear()
                }
            }
            if (buffer.isNotEmpty()) onBatch(Batch(buffer.toList(), scanned, total))
        }
    }

    /** Cached column indices; resolving these per row costs measurable time at 100k rows. */
    private class Columns(cursor: Cursor) {
        val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val artistId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
        val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val albumArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
        val composer = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
        val genre = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
        val track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val bitrate = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
        val mime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val data = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val dateModified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
    }

    private fun Cursor.readSong(c: Columns): SongEntity? {
        val path = getStringOrNull(c.data) ?: return null
        val folder = path.substringBeforeLast('/', "")
        if (folder.isEmpty()) return null

        val id = getLong(c.id)
        // MediaStore encodes disc/track as DDTTT, e.g. 2005 = disc 2, track 5.
        val rawTrack = getIntOrZero(c.track)
        val disc = if (rawTrack > 1000) rawTrack / 1000 else 1
        val track = if (rawTrack > 1000) rawTrack % 1000 else rawTrack
        val title = getStringOrNull(c.title).orEmpty().ifBlank { File(path).nameWithoutExtension }

        return SongEntity(
            id = id,
            title = title,
            titleKey = title.lowercase(Locale.getDefault()),
            artist = getStringOrNull(c.artist).orUnknown(),
            artistId = getLong(c.artistId),
            album = getStringOrNull(c.album).orUnknown(),
            albumId = getLong(c.albumId),
            albumArtist = getStringOrNull(c.albumArtist),
            genre = getStringOrNull(c.genre),
            composer = getStringOrNull(c.composer),
            trackNumber = track,
            discNumber = disc,
            year = getIntOrZero(c.year),
            durationMs = getLong(c.duration),
            sizeBytes = getLong(c.size),
            bitrate = getIntOrZero(c.bitrate),
            sampleRate = 0, // filled in by the enrichment pass
            channels = 0,
            mimeType = getStringOrNull(c.mime).orEmpty(),
            uri = ContentUris.withAppendedId(id).toString(),
            data = path,
            folderPath = folder,
            dateAddedSeconds = getLong(c.dateAdded),
            dateModifiedSeconds = getLong(c.dateModified),
            replayGainTrack = null,
            replayGainAlbum = null,
            hasEmbeddedLyrics = false,
            artworkUri = albumArtUri(getLong(c.albumId)).toString(),
        )
    }

    /** Directories containing a `.nomedia` marker, used to hide folders. */
    fun findNoMediaFolders(roots: Collection<String>): Set<String> =
        roots.filter { File(it, ".nomedia").exists() }.toSet()

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private fun Cursor.getIntOrZero(index: Int): Int =
        if (index >= 0 && !isNull(index)) getInt(index) else 0

    private fun String?.orUnknown(): String = if (isNullOrBlank() || this == "<unknown>") "Unknown" else this

    private object ContentUris {
        fun withAppendedId(id: Long): Uri =
            android.content.ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id,
            )
    }

    private fun albumArtUri(albumId: Long): Uri =
        android.content.ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId,
        )

    companion object {
        /** Tuned so a 100k-song scan writes ~200 transactions instead of 100k. */
        const val BATCH_SIZE = 500

        private val PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.COMPOSER,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
    }
}
