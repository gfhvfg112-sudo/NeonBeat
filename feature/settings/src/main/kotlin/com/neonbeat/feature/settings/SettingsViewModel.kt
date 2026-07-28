package com.neonbeat.feature.settings

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neonbeat.core.datastore.ReplayGainMode
import com.neonbeat.core.datastore.SettingsRepository
import com.neonbeat.core.datastore.ThemeMode
import com.neonbeat.core.datastore.UserSettings
import com.neonbeat.domain.repository.BackupRepository
import com.neonbeat.domain.usecase.RescanLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** One-off messages surfaced as snackbars. */
sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
}

/**
 * Settings state holder.
 *
 * Every setter writes straight through to DataStore instead of mutating local
 * state: the screen renders the persisted value, so what the user sees is
 * always what was actually saved, even if the process is killed mid-change.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val rescanLibrary: RescanLibraryUseCase,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    // ------------------------------------------------------------ appearance

    /** Cycles System -> Light -> Dark -> AMOLED, matching the row's single tap. */
    fun cycleThemeMode() = viewModelScope.launch {
        val next = when (settings.value.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.AMOLED
            ThemeMode.AMOLED -> ThemeMode.SYSTEM
        }
        settingsRepository.setThemeMode(next)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDynamicColor(enabled)
    }

    fun setGlassEffects(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setGlassmorphism(enabled)
    }

    fun setCornerRadius(dp: Int) = viewModelScope.launch {
        settingsRepository.setCornerRadius(dp)
    }

    fun setBlurRadius(dp: Int) = viewModelScope.launch {
        settingsRepository.setBlurRadius(dp)
    }

    fun setGridColumns(columns: Int) = viewModelScope.launch {
        settingsRepository.setGridColumns(columns)
    }

    // -------------------------------------------------------------- playback

    fun setGapless(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setGapless(enabled)
    }

    fun setCrossfade(seconds: Int) = viewModelScope.launch {
        settingsRepository.setCrossfade(seconds)
    }

    /** ReplayGain is a mode, but the row exposes it as a simple on/off switch. */
    fun setReplayGain(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setReplayGain(if (enabled) ReplayGainMode.AUTO else ReplayGainMode.OFF)
    }

    fun setMonoAudio(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setMono(enabled)
    }

    fun setSkipSilence(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setSkipSilence(enabled)
    }

    fun setBitPerfect(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBitPerfect(enabled)
    }

    fun setPreAmp(db: Float) = viewModelScope.launch {
        settingsRepository.setPreAmp(db)
    }

    // --------------------------------------------------------------- effects

    fun setEqualizerEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setEqualizerEnabled(enabled)
    }

    fun setBassBoost(strength: Int) = viewModelScope.launch {
        settingsRepository.setBassBoost(strength)
    }

    fun setVirtualizer(strength: Int) = viewModelScope.launch {
        settingsRepository.setVirtualizer(strength)
    }

    fun setLoudnessGain(millibels: Int) = viewModelScope.launch {
        settingsRepository.setLoudnessGain(millibels)
    }

    // --------------------------------------------------------------- library

    fun rescanLibrary() = viewModelScope.launch {
        val result = runCatching { rescanLibrary(force = true) }.getOrNull()
        _events.send(
            SettingsEvent.Message(
                result?.let { "Scan complete: ${it.added} added, ${it.removed} removed" }
                    ?: "Scan failed",
            ),
        )
    }

    // ---------------------------------------------------------------- backup

    fun exportBackup() = viewModelScope.launch {
        val target = File(defaultBackupDir(), BACKUP_FILE).path
        val ok = runCatching { backupRepository.exportBackup(target) }.getOrDefault(false)
        _events.send(
            SettingsEvent.Message(if (ok) "Backup saved to $target" else "Backup failed"),
        )
    }

    fun importBackup() = viewModelScope.launch {
        val source = File(defaultBackupDir(), BACKUP_FILE).path
        val ok = runCatching { backupRepository.importBackup(source) }.getOrDefault(false)
        _events.send(
            SettingsEvent.Message(if (ok) "Backup restored" else "No readable backup at $source"),
        )
    }

    /**
     * Backups go to the public Documents folder so they survive uninstall and
     * can be copied off the device without a file picker round trip.
     */
    private fun defaultBackupDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

    private companion object {
        const val BACKUP_FILE = "neonbeat-backup.json"
    }
}
