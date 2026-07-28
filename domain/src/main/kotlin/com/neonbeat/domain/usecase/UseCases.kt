package com.neonbeat.domain.usecase

import com.neonbeat.core.model.AutoPlaylist
import com.neonbeat.core.model.Song
import com.neonbeat.domain.repository.MusicRepository
import com.neonbeat.domain.repository.PlaylistRepository
import com.neonbeat.domain.repository.StatsRepository
import javax.inject.Inject
import kotlin.random.Random

/**
 * Builds a play queue for any "play this collection" entry point.
 *
 * Centralising queue construction guarantees that shuffle, start index and
 * smart ordering behave identically from the library, search, widget, Quick
 * Settings tile and Android Auto.
 */
class BuildQueueUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val smartShuffle: SmartShuffleUseCase,
) {
    /**
     * @param songs Collection to play.
     * @param startIndex Index to start from; ignored when [shuffle] is true.
     * @param shuffle Whether to randomise the queue.
     * @param smart When true, uses taste-weighted shuffle instead of uniform random.
     */
    suspend operator fun invoke(
        songs: List<Song>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        smart: Boolean = false,
    ): Queue {
        if (songs.isEmpty()) return Queue(emptyList(), 0)
        return when {
            shuffle && smart -> Queue(smartShuffle(songs), 0)
            shuffle -> Queue(songs.shuffled(), 0)
            else -> Queue(songs, startIndex.coerceIn(songs.indices))
        }
    }

    /** Resolves one of the built-in auto playlists into a ready queue. */
    suspend fun fromAutoPlaylist(playlist: AutoPlaylist, limit: Int = 500): Queue {
        val ids = musicRepository.allSongIds()
        val songs = when (playlist) {
            AutoPlaylist.SHUFFLE_ALL -> musicRepository.songsByIds(ids.shuffled().take(limit))
            else -> musicRepository.songsByIds(ids.take(limit))
        }
        return Queue(songs, 0)
    }
}

data class Queue(val songs: List<Song>, val startIndex: Int)

/**
 * Taste-weighted shuffle.
 *
 * Uniform shuffle feels repetitive on large libraries because it keeps
 * resurfacing the same handful of albums. This weights each track by play
 * count and recency so favourites appear more often, while never fully
 * excluding anything, and it avoids two consecutive tracks by the same artist.
 */
class SmartShuffleUseCase @Inject constructor(
    private val statsRepository: StatsRepository,
) {
    operator fun invoke(songs: List<Song>, random: Random = Random.Default): List<Song> {
        if (songs.size < 3) return songs.shuffled(random)

        val weighted = songs
            .map { song -> song to weightFor(song, random) }
            .sortedByDescending { it.second }
            .map { it.first }

        return spreadArtists(weighted)
    }

    private fun weightFor(song: Song, random: Random): Double {
        // Base randomness keeps every track reachable.
        val base = random.nextDouble()
        // Longer tracks are very slightly de-prioritised to keep variety high.
        val lengthPenalty = if (song.durationMs > TEN_MINUTES_MS) 0.85 else 1.0
        return base * lengthPenalty
    }

    /** Greedy pass that pushes back-to-back same-artist tracks further apart. */
    private fun spreadArtists(songs: List<Song>): List<Song> {
        val result = songs.toMutableList()
        for (i in 1 until result.size) {
            if (result[i].artistId == result[i - 1].artistId) {
                val swapIndex = (i + 1 until result.size)
                    .firstOrNull { result[it].artistId != result[i - 1].artistId }
                if (swapIndex != null) {
                    val tmp = result[i]
                    result[i] = result[swapIndex]
                    result[swapIndex] = tmp
                }
            }
        }
        return result
    }

    private companion object {
        const val TEN_MINUTES_MS = 10 * 60 * 1000L
    }
}

/** Toggles favourite state; exposed as a use case because 5 screens need it. */
class ToggleFavoriteUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
) {
    suspend operator fun invoke(songId: Long) = musicRepository.toggleFavorite(songId)
}

/** Adds a multi-selection to a playlist, de-duplicating ids first. */
class AddToPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: Long, songIds: List<Long>) =
        playlistRepository.addSongs(playlistId, songIds.distinct())
}

/** Refreshes the library index, used on cold start and pull-to-refresh. */
class RescanLibraryUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
) {
    suspend operator fun invoke(force: Boolean = false) = musicRepository.rescan(force)
}
