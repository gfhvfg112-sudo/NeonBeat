package com.neonbeat.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import com.neonbeat.core.common.di.IoDispatcher
import com.neonbeat.core.database.dao.LyricsDao
import com.neonbeat.core.database.dao.SongDao
import com.neonbeat.core.database.entity.LyricsEntity
import com.neonbeat.core.model.Lyrics
import com.neonbeat.core.model.LyricsSource
import com.neonbeat.data.lyrics.LrcParser
import com.neonbeat.domain.repository.LyricsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves lyrics for a track, in priority order:
 *
 * 1. User-saved or previously cached lyrics in Room
 * 2. A sidecar `.lrc` file next to the audio file (synced)
 * 3. Lyrics embedded in the file's metadata (usually unsynced)
 *
 * Whatever is resolved from disk is written back to Room so the next lookup is
 * a single indexed read — important because this runs on every track change.
 *
 * Online lyric download is intentionally not implemented: it needs a provider
 * choice and a network permission policy. [downloadLyrics] is the seam where
 * that provider plugs in.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lyricsDao: LyricsDao,
    private val songDao: SongDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LyricsRepository {

    override fun lyrics(songId: Long): Flow<Lyrics?> = flow {
        lyricsDao.getBySongId(songId)?.let { cached ->
            emit(cached.toLyrics())
            return@flow
        }

        val song = songDao.getById(songId)
        if (song == null) {
            emit(null)
            return@flow
        }

        val sidecar = readSidecar(song.data)
        if (sidecar != null) {
            val entity = LyricsEntity(
                songId = songId,
                content = sidecar,
                synced = true,
                source = LyricsSource.LRC_FILE.name,
            )
            lyricsDao.upsert(entity)
            emit(entity.toLyrics())
            return@flow
        }

        val embedded = readEmbedded(song.data)
        if (embedded != null) {
            val parsed = LrcParser.parse(embedded)
            val entity = LyricsEntity(
                songId = songId,
                content = embedded,
                synced = parsed.any { it.timeMs != null },
                source = LyricsSource.EMBEDDED.name,
            )
            lyricsDao.upsert(entity)
            emit(entity.toLyrics())
            return@flow
        }

        emit(null)
    }.flowOn(ioDispatcher)

    override suspend fun saveLyrics(songId: Long, content: String) = withContext(ioDispatcher) {
        val parsed = LrcParser.parse(content)
        lyricsDao.upsert(
            LyricsEntity(
                songId = songId,
                content = content,
                synced = parsed.any { it.timeMs != null },
                source = LyricsSource.USER.name,
            ),
        )
    }

    override suspend fun clearLyrics(songId: Long) = withContext(ioDispatcher) {
        lyricsDao.deleteBySongId(songId)
    }

    /**
     * Placeholder for online lyric fetching.
     *
     * Not implemented: shipping this requires choosing a lyrics provider and
     * declaring the matching network/privacy policy. Returns `false` so callers
     * can surface an honest "not available" state instead of failing silently.
     */
    override suspend fun downloadLyrics(songId: Long): Boolean = false

    /** Looks for `Song.lrc` beside `Song.mp3`, the de-facto convention. */
    private fun readSidecar(path: String): String? = runCatching {
        val audioFile = File(path)
        val lrc = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".lrc")
        if (lrc.exists() && lrc.length() < MAX_LYRICS_BYTES) lrc.readText() else null
    }.getOrNull()

    private fun readEmbedded(path: String): String? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun LyricsEntity.toLyrics(): Lyrics {
        val lines = LrcParser.parse(content)
        return Lyrics(
            songId = songId,
            lines = lines,
            synced = lines.any { it.timeMs != null },
            source = runCatching { LyricsSource.valueOf(source) }.getOrDefault(LyricsSource.EMBEDDED),
        )
    }

    private companion object {
        const val MAX_LYRICS_BYTES = 512 * 1024L
    }
}
