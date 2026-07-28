package com.neonbeat.core.media.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.neonbeat.core.database.dao.QueueDao
import com.neonbeat.core.database.dao.StatsDao
import com.neonbeat.core.database.entity.PlayHistoryEntity
import com.neonbeat.core.database.entity.QueueItemEntity
import com.neonbeat.core.datastore.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Sleep timer with a smooth fade-out tail.
 *
 * The fade runs on the player volume (not the system stream) so it never
 * touches the user's media volume, and the timer survives track changes.
 */
@Singleton
class SleepTimerController @Inject constructor(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
) {
    private val _remainingMs = MutableStateFlow<Long?>(null)

    /** `null` when no timer is armed, otherwise the countdown in milliseconds. */
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    private var player: Player? = null
    private var job: Job? = null
    private var finishTrackFirst = false

    fun attach(player: ExoPlayer) {
        this.player = player
    }

    fun detach() {
        cancel()
        player = null
    }

    /**
     * Arms the timer.
     *
     * @param durationMs How long to keep playing.
     * @param finishCurrentTrack When true, playback stops at the end of the
     *   current track instead of mid-song once the countdown elapses.
     * @param fadeOutSeconds Length of the volume ramp applied before stopping.
     */
    fun start(durationMs: Long, finishCurrentTrack: Boolean = false, fadeOutSeconds: Int = 10) {
        cancel()
        finishTrackFirst = finishCurrentTrack
        job = scope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                _remainingMs.value = remaining
                delay(TICK_MS)
                remaining -= TICK_MS
            }
            _remainingMs.value = 0
            if (finishTrackFirst) awaitTrackEnd()
            fadeOutAndPause(fadeOutSeconds)
            _remainingMs.value = null
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _remainingMs.value = null
        player?.volume = 1f
    }

    private suspend fun awaitTrackEnd() {
        val p = player ?: return
        while (p.isPlaying && p.duration > 0 && p.currentPosition < p.duration - 500) {
            delay(TICK_MS)
        }
    }

    /** Equal-power ramp: linear volume ramps sound abrupt at the end. */
    private suspend fun fadeOutAndPause(seconds: Int) {
        val p = player ?: return
        val steps = (seconds * 1000L / FADE_STEP_MS).toInt().coerceAtLeast(1)
        repeat(steps) { step ->
            val progress = 1f - (step + 1).toFloat() / steps
            p.volume = progress.pow(2f)
            delay(FADE_STEP_MS)
        }
        p.pause()
        p.volume = 1f
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val FADE_STEP_MS = 50L
    }
}

/**
 * Persists queue and listening statistics as playback progresses.
 *
 * A play is only counted once the listener has heard the smaller of half the
 * track or four minutes, matching the scrobbling convention and preventing
 * skip-spam from polluting "most played".
 */
@Singleton
class PlaybackStateWriter @Inject constructor(
    private val scope: CoroutineScope,
    private val queueDao: QueueDao,
    private val statsDao: StatsDao,
) {
    private var player: Player? = null
    private var currentSongId: Long? = null
    private var accumulatedMs: Long = 0
    private var lastTickMs: Long = 0
    private var counted = false

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            flushCurrent(completed = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            currentSongId = mediaItem?.mediaId?.toLongOrNull()
            accumulatedMs = 0
            counted = false
            lastTickMs = System.currentTimeMillis()
            persistQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val now = System.currentTimeMillis()
            if (isPlaying) {
                lastTickMs = now
            } else {
                accumulatedMs += now - lastTickMs
                maybeCountPlay()
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) persistQueue()
        }
    }

    fun attach(player: ExoPlayer) {
        this.player = player
        player.addListener(listener)
    }

    fun detach() {
        flushCurrent(completed = false)
        player?.removeListener(listener)
        player = null
    }

    private fun persistQueue() {
        val p = player ?: return
        val items = (0 until p.mediaItemCount).mapNotNull { index ->
            p.getMediaItemAt(index).mediaId.toLongOrNull()?.let { songId ->
                QueueItemEntity(position = index, songId = songId, shuffledPosition = index)
            }
        }
        scope.launch { queueDao.replace(items) }
    }

    private fun maybeCountPlay() {
        val songId = currentSongId ?: return
        val duration = player?.duration ?: C.TIME_UNSET
        if (counted || duration <= 0) return
        val threshold = minOf(duration / 2, 4 * 60_000L)
        if (accumulatedMs >= threshold) {
            counted = true
            val listened = accumulatedMs
            scope.launch {
                statsDao.recordPlay(songId, System.currentTimeMillis(), listened)
                statsDao.insertHistory(
                    PlayHistoryEntity(
                        songId = songId,
                        playedAtEpochMs = System.currentTimeMillis(),
                        listenedMs = listened,
                        completed = false,
                    ),
                )
            }
        }
    }

    private fun flushCurrent(completed: Boolean) {
        val songId = currentSongId ?: return
        val listened = accumulatedMs
        if (!counted && !completed) scope.launch { statsDao.recordSkip(songId) }
        if (completed) {
            scope.launch {
                statsDao.insertHistory(
                    PlayHistoryEntity(
                        songId = songId,
                        playedAtEpochMs = System.currentTimeMillis(),
                        listenedMs = listened,
                        completed = true,
                    ),
                )
            }
        }
    }
}

/**
 * Applies speed, pitch, balance and mono settings to a live player.
 *
 * Speed and pitch are decoupled: ExoPlayer's Sonic backend can change tempo
 * without the chipmunk effect, and pitch without changing tempo.
 */
object PlaybackTuning {

    /** @param semitones Positive shifts up, negative shifts down. */
    fun apply(player: Player, speed: Float, semitones: Float) {
        val pitch = 2f.pow(semitones / 12f)
        player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 4f), pitch)
    }

    /**
     * Balance is emulated on the player volume pair via a channel mixing matrix
     * in [com.neonbeat.core.media.effects.ChannelMixingProcessor]; this helper
     * only reports the per-channel gains.
     *
     * @param balance -1 = full left, 0 = center, 1 = full right.
     */
    fun channelGains(balance: Float, mono: Boolean): Pair<Float, Float> {
        if (mono) return 0.7071f to 0.7071f
        val b = balance.coerceIn(-1f, 1f)
        return (1f - b.coerceAtLeast(0f)) to (1f + b.coerceAtMost(0f))
    }
}
