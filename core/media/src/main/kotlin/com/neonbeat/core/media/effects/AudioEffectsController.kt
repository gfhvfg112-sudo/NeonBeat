package com.neonbeat.core.media.effects

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.neonbeat.core.datastore.SettingsRepository
import com.neonbeat.core.datastore.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every `android.media.audiofx` effect attached to the player's audio session.
 *
 * Effects are hardware-backed where available, so they cost virtually no CPU.
 * All calls are defensive: several OEM ROMs throw when an effect is unsupported,
 * and a missing Virtualizer must never crash playback.
 *
 * @param scope Service-scoped coroutine scope; effects follow settings changes live.
 */
@Singleton
class AudioEffectsController @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var audioSessionId: Int = 0

    /** Number of bands reported by the device equalizer, or 0 when unsupported. */
    val bandCount: Int get() = equalizer?.numberOfBands?.toInt() ?: 0

    /** Center frequency of each band, in Hz. */
    fun bandFrequencies(): List<Int> =
        (0 until bandCount).map { equalizer?.getCenterFreq(it.toShort())?.div(1000) ?: 0 }

    /** Inclusive gain range in millibels, e.g. -1500..1500. */
    fun bandLevelRange(): IntRange {
        val range = equalizer?.bandLevelRange ?: return 0..0
        return range[0].toInt()..range[1].toInt()
    }

    fun presetNames(): List<String> {
        val eq = equalizer ?: return emptyList()
        return (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
    }

    /** Binds all effects to [sessionId] and starts observing settings. */
    fun attach(sessionId: Int) {
        if (sessionId == 0 || sessionId == audioSessionId) return
        release()
        audioSessionId = sessionId

        equalizer = runCatching { Equalizer(EFFECT_PRIORITY, sessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(EFFECT_PRIORITY, sessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(EFFECT_PRIORITY, sessionId) }.getOrNull()
        loudnessEnhancer = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()

        settingsRepository.settings
            .distinctUntilChanged { old, new -> old.effectsSignature() == new.effectsSignature() }
            .onEach(::apply)
            .launchIn(scope)
    }

    /** Applies the effect-related subset of [settings] to the live audio session. */
    fun apply(settings: UserSettings) {
        runCatching {
            equalizer?.let { eq ->
                eq.enabled = settings.equalizerEnabled
                if (settings.equalizerBands.isNotBlank()) {
                    settings.equalizerBands.split(',')
                        .mapNotNull(String::toShortOrNull)
                        .forEachIndexed { index, level ->
                            if (index < eq.numberOfBands) eq.setBandLevel(index.toShort(), level)
                        }
                }
            }
            bassBoost?.let {
                it.enabled = settings.bassBoostStrength > 0
                if (it.strengthSupported) it.setStrength(settings.bassBoostStrength.toShort())
            }
            virtualizer?.let {
                it.enabled = settings.virtualizerStrength > 0
                if (it.strengthSupported) it.setStrength(settings.virtualizerStrength.toShort())
            }
            loudnessEnhancer?.let {
                it.enabled = settings.loudnessGainMb > 0
                it.setTargetGain(settings.loudnessGainMb)
            }
        }
    }

    /** Persists a manual band change and applies it immediately. */
    suspend fun setBandLevel(band: Int, levelMillibels: Int) {
        runCatching { equalizer?.setBandLevel(band.toShort(), levelMillibels.toShort()) }
        val levels = (0 until bandCount).map { equalizer?.getBandLevel(it.toShort())?.toInt() ?: 0 }
        settingsRepository.setEqualizerBands(levels.joinToString(","))
    }

    suspend fun usePreset(presetIndex: Int) {
        val eq = equalizer ?: return
        runCatching { eq.usePreset(presetIndex.toShort()) }
        settingsRepository.setEqualizerPreset(eq.getPresetName(presetIndex.toShort()))
        val levels = (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()).toInt() }
        settingsRepository.setEqualizerBands(levels.joinToString(","))
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        audioSessionId = 0
    }

    private fun UserSettings.effectsSignature() = listOf(
        equalizerEnabled, equalizerBands, bassBoostStrength, virtualizerStrength, loudnessGainMb,
    )

    private companion object {
        /** Priority passed to AudioEffect; higher wins against other apps. */
        const val EFFECT_PRIORITY = 1000
    }
}
