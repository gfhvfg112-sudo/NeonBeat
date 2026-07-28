package com.neonbeat.data.repository

import android.media.MediaMetadataRetriever
import com.neonbeat.core.common.di.IoDispatcher
import com.neonbeat.core.database.dao.LyricsDao
import com.neonbeat.core.database.entity.LyricsEntity
import com.neonbeat.core.model.Lyrics
import com.neonbeat.core.model.LyricsSource
import com.neonbeat.core.model.Song
import com.neonbeat.data.lyrics.LrcParser
import com.neonbeat.domain.repository.LyricsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves lyrics for a track, preferring what the user already has locally.
 *
 * Lookup order:
 * 1. A stored copy in the database (manual edits and previous downloads win).
 * 2. A sidecar `.lrc` file next to the audio file, which is how most desktop
 *    players ship synced lyrics.
 * 3. Tags embedded in the file itself.
 *
 * Anything resolved from disk or tags is cached back into the database so the
 * next lookup is a single indexed read.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val lyricsDao: LyricsDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : LyricsRepository {

    override fun lyrics(song: Song): Flow<Lyrics?> =
        lyricsDao.lyrics(song.id)
            .map { stored -> stored?.toLyrics() ?: resolveAndCache(song) }
            .flowOn(io)

    /**
     * Best-effort refresh.
     *
     * No lyrics provider is bundled, so this re-reads the sidecar file and the
     * embedded tags rather than pretending a network source exists.
     */
    override suspend fun downloadLyrics(song: Song): Lyrics? = withContext(io) {
        resolveAndCache(song, force = true)
    }

    override suspend fun saveManual(songId: Long, content: String) {
        withContext(io) {
            if (content.isBlank()) {
                lyricsDao.delete(songId)
            } else {
                lyricsDao.upsert(
                    LyricsEntity(
                        songId = songId,
                        content = content,
                        source = LyricsSource.MANUAL.name,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private suspend fun resolveAndCache(song: Song, force: Boolean = false): Lyrics? {
        val sidecar = readSidecar(song)
        val resolved = sidecar ?: readEmbedded(song)
        if (resolved == null) return null
        val (content, source) = resolved
        if (force || content.isNotBlank()) {
            lyricsDao.upsert(
                LyricsEntity(
                    songId = song.id,
                    content = content,
                    source = source.name,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
        return Lyrics(songId = song.id, lines = LrcParser.parse(content), source = source)
    }

    /** Looks for `<same-name>.lrc` next to the audio file. */
    private fun readSidecar(song: Song): Pair<String, LyricsSource>? {
        val audio = File(song.data)
        val lrc = File(audio.parentFile, audio.nameWithoutExtension + ".lrc")
        if (!lrc.isFile || lrc.length() > MAX_LYRICS_BYTES) return null
        val text = runCatching { lrc.readText() }.getOrNull() ?: return null
        return if (text.isBlank()) null else text to LyricsSource.LRC_FILE
    }

    /**
     * Reads the embedded lyrics tag.
     *
     * [MediaMetadataRetriever] is only `AutoCloseable` from API 29, so it is
     * released manually to stay compatible with the API 26 minimum.
     */
    private fun readEmbedded(song: Song): Pair<String, LyricsSource>? {
        if (!song.hasEmbeddedLyrics) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(song.data)
            val text = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
            if (text.isNullOrBlank()) null else text to LyricsSource.EMBEDDED
        } catch (_: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val MAX_LYRICS_BYTES = 512 * 1024L
    }
}

private fun LyricsEntity.toLyrics() = Lyrics(
    songId = songId,
    lines = LrcParser.parse(content),
    source = runCatching { LyricsSource.valueOf(source) }.getOrDefault(LyricsSource.MANUAL),
)
