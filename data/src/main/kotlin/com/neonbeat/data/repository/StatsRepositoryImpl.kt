package com.neonbeat.data.repository

import com.neonbeat.core.common.di.IoDispatcher
import com.neonbeat.core.database.dao.StatsDao
import com.neonbeat.core.database.entity.PlayEventEntity
import com.neonbeat.core.model.PlayEvent
import com.neonbeat.core.model.Song
import com.neonbeat.data.mapper.toSong
import com.neonbeat.domain.repository.StatsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listening statistics, history and recommendations.
 *
 * A play is only recorded once the track has been listened to past a
 * meaningful threshold, so skipping through an album does not pollute play
 * counts or "most played".
 */
@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val statsDao: StatsDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StatsRepository {

    override suspend fun recordPlay(
        songId: Long,
        playedMs: Long,
        durationMs: Long,
        completed: Boolean,
    ) = withContext(ioDispatcher) {
        val qualifies = completed ||
            playedMs >= MIN_PLAY_MS ||
            (durationMs > 0 && playedMs >= durationMs / 2)
        if (!qualifies) return@withContext

        val now = System.currentTimeMillis()
        statsDao.insertPlayEvent(
            PlayEventEntity(
                songId = songId,
                playedAt = now,
                playedMs = playedMs,
                completed = completed,
            ),
        )
        statsDao.incrementPlayCount(songId, now)
    }

    override fun history(limit: Int): Flow<List<PlayEvent>> =
        statsDao.observeHistory(limit).map { events ->
            events.map { event ->
                PlayEvent(
                    songId = event.songId,
                    playedAt = event.playedAt,
                    playedMs = event.playedMs,
                    completed = event.completed,
                )
            }
        }

    override fun mostPlayed(limit: Int): Flow<List<Song>> =
        statsDao.observeMostPlayed(limit).map { songs -> songs.map { it.toSong() } }

    override fun recentlyPlayed(limit: Int): Flow<List<Song>> =
        statsDao.observeRecentlyPlayed(limit).map { songs -> songs.map { it.toSong() } }

    override fun totalListeningMs(sinceMs: Long): Flow<Long> = statsDao.observeTotalListened(sinceMs)

    /**
     * Offline recommendations.
     *
     * Picks tracks the user rarely plays but that sit in their most-played
     * genres and artists — a purely local heuristic, with no network calls and
     * no profile data leaving the device.
     */
    override fun recommendations(limit: Int): Flow<List<Song>> =
        statsDao.observeRecommendations(limit).map { songs -> songs.map { it.toSong() } }

    override suspend fun clearHistory() = withContext(ioDispatcher) {
        statsDao.clearHistory()
    }

    private companion object {
        /** 30 s is the long-standing scrobbling convention for a "real" play. */
        const val MIN_PLAY_MS = 30_000L
    }
}
