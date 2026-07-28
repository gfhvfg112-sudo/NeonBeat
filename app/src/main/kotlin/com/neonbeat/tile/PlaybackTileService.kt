package com.neonbeat.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.neonbeat.core.media.PlaybackConnection
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Quick Settings tile for play/pause.
 *
 * The tile observes the media session only while it is visible
 * ([onStartListening] to [onStopListening]), which is the contract the system
 * expects and keeps the app from holding a controller in the background.
 */
@AndroidEntryPoint
class PlaybackTileService : TileService() {

    @Inject
    lateinit var playbackConnection: PlaybackConnection

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        playbackConnection.state
            .onEach { state -> render(isPlaying = state.isPlaying, title = state.currentTitle) }
            .launchIn(newScope)
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        playbackConnection.togglePlayPause()
    }

    private fun render(isPlaying: Boolean, title: String?) {
        qsTile?.apply {
            state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "NeonBeat"
            subtitle = title ?: if (isPlaying) "Playing" else "Paused"
            updateTile()
        }
    }
}
