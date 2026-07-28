package com.neonbeat.core.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.neonbeat.core.media.service.MusicService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing snapshot of the player, kept deliberately tiny for cheap recomposition. */
data class PlaybackState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val currentSongId: Long? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val queueSize: Int = 0,
    val queueIndex: Int = 0,
    val abRepeat: Pair<Long, Long>? = null,
)

/**
 * Single entry point the UI uses to talk to [MusicService].
 *
 * Wraps the async [MediaController] handshake and republishes player callbacks
 * as a [StateFlow], so screens never touch Media3 listeners directly.
 */
@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var abStart: Long? = null
    private var abEnd: Long? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    /** Connects to the session; safe to call repeatedly. */
    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                controller = future.get().also { c ->
                    c.addListener(listener)
                    publish(c)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _state.value = PlaybackState()
    }

    /**
     * Replaces the queue and starts playback.
     *
     * @param startIndex Index within [items] to begin from.
     */
    fun playQueue(items: List<MediaItem>, startIndex: Int = 0, positionMs: Long = 0) {
        val c = controller ?: return
        c.setMediaItems(items, startIndex, positionMs)
        c.prepare()
        c.play()
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)
    fun setShuffle(enabled: Boolean) { controller?.shuffleModeEnabled = enabled }
    fun setRepeatMode(mode: Int) { controller?.repeatMode = mode }
    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        controller?.playbackParameters = PlaybackParameters(speed, pitch)
    }

    /** Appends to the end of the queue ("swipe to queue"). */
    fun addToQueue(items: List<MediaItem>) = controller?.addMediaItems(items)

    /** Inserts directly after the current item ("play next"). */
    fun playNext(items: List<MediaItem>) {
        val c = controller ?: return
        c.addMediaItems(c.currentMediaItemIndex + 1, items)
    }

    fun moveQueueItem(from: Int, to: Int) = controller?.moveMediaItem(from, to)
    fun removeQueueItem(index: Int) = controller?.removeMediaItem(index)

    /**
     * Sets or clears the A-B repeat loop.
     *
     * First call marks A, second marks B, third clears the loop.
     */
    fun cycleAbRepeat() {
        val position = controller?.currentPosition ?: return
        when {
            abStart == null -> abStart = position
            abEnd == null -> abEnd = position
            else -> { abStart = null; abEnd = null }
        }
        controller?.let(::publish)
    }

    /** Called on each position tick to enforce the A-B loop. */
    fun enforceAbRepeat() {
        val c = controller ?: return
        val start = abStart ?: return
        val end = abEnd ?: return
        if (c.currentPosition >= end) c.seekTo(start)
    }

    private fun publish(player: Player) {
        _state.value = PlaybackState(
            isConnected = true,
            isPlaying = player.isPlaying,
            currentSongId = player.currentMediaItem?.mediaId?.toLongOrNull(),
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            bufferedMs = player.bufferedPosition.coerceAtLeast(0),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            speed = player.playbackParameters.speed,
            queueSize = player.mediaItemCount,
            queueIndex = player.currentMediaItemIndex,
            abRepeat = abStart?.let { start -> abEnd?.let { end -> start to end } },
        )
    }
}
