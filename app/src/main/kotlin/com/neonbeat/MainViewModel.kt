package com.neonbeat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neonbeat.core.datastore.SettingsRepository
import com.neonbeat.core.datastore.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the state the activity needs before it can draw its first frame.
 *
 * [isReady] gates the splash screen. It flips as soon as the persisted theme
 * has been read — a single DataStore read — so the app never blocks the splash
 * on the library scan or on connecting to the media session.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    init {
        viewModelScope.launch {
            settingsRepository.settings.first()
            _isReady.value = true
        }
    }
}
