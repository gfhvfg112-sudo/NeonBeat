package com.neonbeat.data.repository

import com.neonbeat.core.common.di.IoDispatcher
import com.neonbeat.core.database.dao.SongDao
import com.neonbeat.core.database.dao.StatsDao
import com.neonbeat.core.model.Song
import com.neonbeat.data.mapper.toSong
import com.neonbeat.domain.repository.StatsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listening statistics, history and lightweight recommendations.
 *
 * Everything is derived from the `song_stats` and `play_history` tables that
 * the playback service writes, so the screens stay accurate offline and never
 * need a network round trip.
 */
@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val statsDao: StatsDao,
    private val songDao: SongDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : StatsRepository {

    override fun listeningMinutes(sinceEpochMs: Long): Flow<Long> =
        statsDao.listenedMsSince(sinceEpochMs)
            .map { it / MILLIS_PER_MINUTE }
            .flowOn(io)

    override fun topArtists(limit: Int): Flow<List<Pair<String, Int>>> =
        statsDao.topArtists(limit)
            .map { rows -> rows.map { it.name to it.playCount } }
            .flowOn(io)

    /**
     * Recently played tracks in play order.
     *
     * History rows only store ids, so the songs are fetched in a single batch
     * and re-ordered in memory; that keeps the query count at two regardless of
     * how long the requested window is.
     */
    override fun history(limit: Int): Flow<List<Song>> =
        statsDao.history(limit)
            .map { events ->
                val byId = songDao.songsByIds(events.map { it.songId }.distinct())
                    .associateBy { it.id }
                events.mapNotNull { byId[it.songId]?.toSong() }
            }
            .flowOn(io)

    override suspend fun recommendations(limit: Int): List<Song> = withContext(io) {
        statsDao.recommendations(limit).map { it.toSong() }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
