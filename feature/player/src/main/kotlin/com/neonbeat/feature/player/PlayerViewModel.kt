package com.neonbeat.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neonbeat.core.media.PlaybackConnection
import com.neonbeat.core.media.PlaybackState
import com.neonbeat.core.model.Lyrics
import com.neonbeat.core.model.Song
import com.neonbeat.domain.repository.LyricsRepository
import com.neonbeat.domain.repository.MusicRepository
import com.neonbeat.domain.repository.PlaylistRepository
import com.neonbeat.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Local, screen-only state that does not belong to the playback session. */
data class PlayerUiState(
    val lyricsExpanded: Boolean = false,
    val queueExpanded: Boolean = false,
    val artworkScale: Float = 1f,
    val sleepTimerMinutes: Int? = null,
)

/**
 * Backing state holder for the now-playing screen.
 *
 * All playback truth lives in the media session and is observed through
 * [PlaybackConnection]; this view model only adds screen-local concerns
 * (lyrics, gesture state, sheet expansion) on top of it. That keeps the UI in
 * sync when playback is changed from the notification, Bluetooth, Android Auto
 * or the widget.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackConnection: PlaybackConnection,
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val lyricsRepository: LyricsRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val playback: StateFlow<PlaybackState> = playbackConnection.state

    private val currentSongId: Flow<Long?> = playbackConnection.state
        .map { it.currentSongId }
        .distinctUntilChanged()

    /**
     * Full record for the current track.
     *
     * The session only carries the media id, so the row is resolved from the
     * library index; `mapLatest` cancels the lookup when the user skips again.
     */
    val currentSong: StateFlow<Song?> = currentSongId
        .mapLatest { id -> id?.let { musicRepository.songById(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isFavorite: StateFlow<Boolean> = currentSongId
        .flatMapLatest { id -> if (id == null) flowOf(false) else musicRepository.isFavorite(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Lyrics for the current track.
     *
     * `flatMapLatest` on the song means switching tracks cancels the previous
     * lookup immediately, so rapid skipping never leaves stale lyrics on screen.
     */
    val lyrics: StateFlow<Lyrics?> = currentSong
        .flatMapLatest { song -> if (song == null) flowOf(null) else lyricsRepository.lyrics(song) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ------------------------------------------------------------- transport

    fun playPause() = playbackConnection.togglePlayPause()
    fun next() = playbackConnection.skipToNext()
    fun previous() = playbackConnection.skipToPrevious()
    fun seekTo(positionMs: Long) { playbackConnection.seekTo(positionMs) }
    fun cycleRepeatMode() = playbackConnection.cycleRepeatMode()
    fun toggleShuffle() = playbackConnection.toggleShuffle()
    fun setSpeed(speed: Float) = playbackConnection.setPlaybackSpeed(speed)
    fun setPitch(pitch: Float) = playbackConnection.setPitch(pitch)

    /** Seek by a relative amount; used by the double-tap-to-seek gesture. */
    fun seekBy(deltaMs: Long) {
        val current = playback.value.positionMs
        val duration = playback.value.durationMs
        seekTo((current + deltaMs).coerceIn(0L, duration.coerceAtLeast(0L)))
    }

    // ----------------------------------------------------------------- queue

    fun moveQueueItem(from: Int, to: Int) { playbackConnection.moveQueueItem(from, to) }
    fun removeQueueItem(index: Int) { playbackConnection.removeQueueItem(index) }
    fun playQueueIndex(index: Int) = playbackConnection.skipToQueueItem(index)

    /** Snapshots the live queue into a new manual playlist. */
    fun saveQueueAsPlaylist(name: String) = viewModelScope.launch {
        val songIds = playbackConnection.queueSongIds()
        if (songIds.isEmpty()) return@launch
        val playlistId = playlistRepository.create(name)
        playlistRepository.addSongs(playlistId, songIds)
    }

    // ------------------------------------------------------------ extras

    fun favoriteCurrent() = viewModelScope.launch {
        playback.value.currentSongId?.let { toggleFavorite(it) }
    }

    /**
     * Screen-level sleep timer selection.
     *
     * The countdown itself runs in the playback service; this only records what
     * the user picked so the sheet can show the active choice.
     */
    fun setSleepTimer(minutes: Int?) {
        _uiState.update { it.copy(sleepTimerMinutes = minutes) }
    }

    fun markAbRepeatPoint() = playbackConnection.markAbRepeatPoint()
    fun clearAbRepeat() = playbackConnection.clearAbRepeat()

    fun toggleLyrics() = _uiState.update { it.copy(lyricsExpanded = !it.lyricsExpanded) }
    fun toggleQueue() = _uiState.update { it.copy(queueExpanded = !it.queueExpanded) }

    /** Pinch-to-zoom on the artwork; clamped so the layout can never break. */
    fun onArtworkPinch(scaleDelta: Float) = _uiState.update {
        it.copy(artworkScale = (it.artworkScale * scaleDelta).coerceIn(0.8f, 1.6f))
    }
}
