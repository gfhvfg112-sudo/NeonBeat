package com.neonbeat.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.neonbeat.core.model.Album
import com.neonbeat.core.model.Artist
import com.neonbeat.core.model.Genre
import com.neonbeat.core.model.MusicFolder
import com.neonbeat.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tabs shown by [LibraryScreen], in display order. */
enum class LibraryTab {
    SONGS,
    ALBUMS,
    ARTISTS,
    GENRES,
    FOLDERS,
    PLAYLISTS,
}

/**
 * Screen state for the library.
 *
 * Paging streams are intentionally kept out of this class: they are exposed
 * separately so a tab switch or a selection change never invalidates the
 * pager and forces a reload.
 */
data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.SONGS,
    val isScanning: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedSongIds: Set<Long> = emptySet(),
    val songCount: Int = 0,
    val lastScanMessage: String? = null,
)

/**
 * Drives [LibraryScreen].
 *
 * All list data comes from Room through the repository, so the screen renders
 * instantly on cold start and a MediaStore rescan is only ever an explicit,
 * user-initiated refresh.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /** `cachedIn` keeps loaded pages across configuration changes and tab switches. */
    val songs = musicRepository.songs().cachedIn(viewModelScope)
    val albums = musicRepository.albums().cachedIn(viewModelScope)
    val artists = musicRepository.artists().cachedIn(viewModelScope)

    val genres: StateFlow<List<Genre>> = musicRepository.genres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val folders: StateFlow<List<MusicFolder>> = musicRepository.folders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch {
            musicRepository.songCount().collect { count ->
                _uiState.update { it.copy(songCount = count) }
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Rescans MediaStore.
     *
     * Guarded so overlapping pull-to-refresh gestures cannot start a second
     * scan while one is already writing to the database.
     */
    fun rescan(force: Boolean = false) {
        if (_uiState.value.isScanning) return
        _uiState.update { it.copy(isScanning = true, lastScanMessage = null) }
        viewModelScope.launch {
            val result = runCatching { musicRepository.rescan(force) }
            _uiState.update { state ->
                state.copy(
                    isScanning = false,
                    lastScanMessage = result.fold(
                        onSuccess = { "Indexed ${it.added} tracks in ${it.durationMs} ms" },
                        onFailure = { "Scan failed: ${it.message ?: "unknown error"}" },
                    ),
                )
            }
        }
    }

    /** Long-press toggles multi-select; clearing the last item exits the mode. */
    fun toggleSelection(songId: Long) {
        _uiState.update { state ->
            val selected = if (songId in state.selectedSongIds) {
                state.selectedSongIds - songId
            } else {
                state.selectedSongIds + songId
            }
            state.copy(selectedSongIds = selected, selectionMode = selected.isNotEmpty())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedSongIds = emptySet(), selectionMode = false) }
    }

    fun favorite(songId: Long) {
        viewModelScope.launch { musicRepository.toggleFavorite(songId) }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedSongIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            musicRepository.deleteSongs(ids)
            clearSelection()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
