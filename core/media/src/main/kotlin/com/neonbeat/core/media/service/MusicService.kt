package com.neonbeat.core.media.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.neonbeat.core.media.effects.AudioEffectsController
import com.neonbeat.core.media.playback.PlaybackStateWriter
import com.neonbeat.core.media.playback.SleepTimerController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground playback service.
 *
 * Extending [MediaLibraryService] (rather than plain `MediaSessionService`)
 * gives us the browse tree required by Android Auto, Wear OS and Assistant for
 * free, while the same [MediaSession] drives the notification and lock screen.
 */
@AndroidEntryPoint
class MusicService : MediaLibraryService() {

    @Inject lateinit var playerFactory: NeonPlayerFactory
    @Inject lateinit var librarySessionCallback: MediaLibrarySessionCallback
    @Inject lateinit var effectsController: AudioEffectsController
    @Inject lateinit var sleepTimerController: SleepTimerController
    @Inject lateinit var playbackStateWriter: PlaybackStateWriter

    private var mediaSession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val player = playerFactory.create()
        effectsController.attach(player.audioSessionId)
        sleepTimerController.attach(player)
        playbackStateWriter.attach(player)

        mediaSession = MediaLibrarySession.Builder(this, player, librarySessionCallback)
            .setSessionActivity(openAppIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    /** Stops the service when the user swipes the task away with playback paused. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return stopSelf()
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sleepTimerController.detach()
        playbackStateWriter.detach()
        effectsController.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * Builds the shared [ExoPlayer] instance.
 *
 * Key choices:
 * - `setSkipSilenceEnabled` and gapless are handled by ExoPlayer natively.
 * - Audio offload is requested so long playback sessions barely touch the CPU,
 *   which is the single biggest battery win on modern SoCs.
 * - Audio focus and `setHandleAudioBecomingNoisy` cover headset/Bluetooth
 *   disconnect behaviour without any manual BroadcastReceiver.
 */
class NeonPlayerFactory @Inject constructor(
    private val context: android.content.Context,
    private val mediaSourceFactory: NeonMediaSourceFactory,
) {
    fun create(): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableAudioTrackPlaybackParams(true)
            .setEnableDecoderFallback(true)

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory.create())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                // Gapless playback is inherent to ExoPlayer's concatenation; we only
                // need to keep the buffers warm across item transitions.
                pauseAtEndOfMediaItems = false
            }
    }
}
