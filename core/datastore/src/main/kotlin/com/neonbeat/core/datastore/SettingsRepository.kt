package com.neonbeat.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "neonbeat_settings")

/** Theme mode selector, including a true-black variant for OLED panels. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/** Visual style of the now-playing screen. */
enum class PlayerStyle { CLASSIC, IMMERSIVE, GLASS, MINIMAL, CAROUSEL, FULL_ART }

/** Bottom bar vs. navigation rail vs. drawer; adaptive layouts may override. */
enum class NavigationStyle { BOTTOM_BAR, RAIL, DRAWER, FLOATING }

enum class VisualizerStyle { NONE, WAVEFORM, CIRCULAR_SPECTRUM, BARS, PARTICLES, FLUID }

enum class ReplayGainMode { OFF, TRACK, ALBUM, AUTO }

enum class AppFont { SYSTEM, INTER, ROBOTO_FLEX, LEXEND, VAZIRMATN }

/**
 * Every user-tunable preference in one strongly typed snapshot.
 *
 * Keeping settings in a single immutable object means Compose screens can
 * observe one flow and skip recomposition when unrelated keys change.
 */
data class UserSettings(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val accentColor: Int = 0xFF66E0FF.toInt(),
    val font: AppFont = AppFont.SYSTEM,
    val cornerRadiusDp: Int = 20,
    val useGlassmorphism: Boolean = true,
    val blurRadiusDp: Int = 32,
    val gridColumns: Int = 2,
    val showAlbumArtInList: Boolean = true,
    val animatedAlbumArt: Boolean = true,
    // Navigation
    val navigationStyle: NavigationStyle = NavigationStyle.BOTTOM_BAR,
    val playerStyle: PlayerStyle = PlayerStyle.IMMERSIVE,
    val enabledLibraryTabs: Set<String> = setOf("songs", "albums", "artists", "genres", "folders", "playlists"),
    // Playback
    val gaplessPlayback: Boolean = true,
    val crossfadeSeconds: Int = 0,
    val replayGainMode: ReplayGainMode = ReplayGainMode.AUTO,
    val preAmpDb: Float = 0f,
    val playbackSpeed: Float = 1f,
    val pitchSemitones: Float = 0f,
    val balance: Float = 0f,
    val monoOutput: Boolean = false,
    val resumeOnHeadsetConnect: Boolean = false,
    val pauseOnHeadsetDisconnect: Boolean = true,
    val skipSilence: Boolean = false,
    val audioOffload: Boolean = true,
    val bitPerfect: Boolean = false,
    val preferUsbDac: Boolean = true,
    val sleepFadeOutSeconds: Int = 10,
    // Effects
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = "Flat",
    val equalizerBands: String = "",
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val loudnessGainMb: Int = 0,
    // Library
    val ignoreShortTracksSeconds: Int = 5,
    val respectNoMedia: Boolean = true,
    val hiddenFolders: Set<String> = emptySet(),
    val autoScanOnStart: Boolean = true,
    val downloadArtwork: Boolean = false,
    val downloadLyrics: Boolean = false,
    // Gestures
    val swipeLeftAction: String = "add_to_queue",
    val swipeRightAction: String = "favorite",
    val doubleTapAction: String = "play_pause",
    val volumeGestureEnabled: Boolean = true,
    val brightnessGestureEnabled: Boolean = true,
    // Extras
    val visualizerStyle: VisualizerStyle = VisualizerStyle.BARS,
    val floatingLyricsEnabled: Boolean = false,
    val lyricsAutoScroll: Boolean = true,
)

/**
 * Reads and writes [UserSettings] through Preferences DataStore.
 *
 * All writes are suspending and atomic; readers get a cold [Flow] that emits
 * the full settings snapshot on every change.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val accentColor = intPreferencesKey("accent_color")
        val font = stringPreferencesKey("font")
        val cornerRadius = intPreferencesKey("corner_radius")
        val glass = booleanPreferencesKey("glassmorphism")
        val blurRadius = intPreferencesKey("blur_radius")
        val gridColumns = intPreferencesKey("grid_columns")
        val showArt = booleanPreferencesKey("show_album_art")
        val animatedArt = booleanPreferencesKey("animated_album_art")
        val navStyle = stringPreferencesKey("nav_style")
        val playerStyle = stringPreferencesKey("player_style")
        val tabs = stringSetPreferencesKey("library_tabs")
        val gapless = booleanPreferencesKey("gapless")
        val crossfade = intPreferencesKey("crossfade_seconds")
        val replayGain = stringPreferencesKey("replay_gain")
        val preAmp = floatPreferencesKey("pre_amp_db")
        val speed = floatPreferencesKey("playback_speed")
        val pitch = floatPreferencesKey("pitch_semitones")
        val balance = floatPreferencesKey("balance")
        val mono = booleanPreferencesKey("mono")
        val resumeOnConnect = booleanPreferencesKey("resume_on_connect")
        val pauseOnDisconnect = booleanPreferencesKey("pause_on_disconnect")
        val skipSilence = booleanPreferencesKey("skip_silence")
        val offload = booleanPreferencesKey("audio_offload")
        val bitPerfect = booleanPreferencesKey("bit_perfect")
        val usbDac = booleanPreferencesKey("prefer_usb_dac")
        val sleepFade = intPreferencesKey("sleep_fade_seconds")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val eqPreset = stringPreferencesKey("eq_preset")
        val eqBands = stringPreferencesKey("eq_bands")
        val bass = intPreferencesKey("bass_boost")
        val virtualizer = intPreferencesKey("virtualizer")
        val loudness = intPreferencesKey("loudness_gain")
        val minDuration = intPreferencesKey("min_duration_seconds")
        val respectNoMedia = booleanPreferencesKey("respect_nomedia")
        val hiddenFolders = stringSetPreferencesKey("hidden_folders")
        val autoScan = booleanPreferencesKey("auto_scan")
        val downloadArtwork = booleanPreferencesKey("download_artwork")
        val downloadLyrics = booleanPreferencesKey("download_lyrics")
        val swipeLeft = stringPreferencesKey("swipe_left")
        val swipeRight = stringPreferencesKey("swipe_right")
        val doubleTap = stringPreferencesKey("double_tap")
        val volumeGesture = booleanPreferencesKey("volume_gesture")
        val brightnessGesture = booleanPreferencesKey("brightness_gesture")
        val visualizer = stringPreferencesKey("visualizer")
        val floatingLyrics = booleanPreferencesKey("floating_lyrics")
        val lyricsAutoScroll = booleanPreferencesKey("lyrics_auto_scroll")
    }

    /** Emits the full settings snapshot; defaults apply to any unset key. */
    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        val d = UserSettings()
        UserSettings(
            themeMode = p[Keys.themeMode]?.let { enumOrNull<ThemeMode>(it) } ?: d.themeMode,
            useDynamicColor = p[Keys.dynamicColor] ?: d.useDynamicColor,
            accentColor = p[Keys.accentColor] ?: d.accentColor,
            font = p[Keys.font]?.let { enumOrNull<AppFont>(it) } ?: d.font,
            cornerRadiusDp = p[Keys.cornerRadius] ?: d.cornerRadiusDp,
            useGlassmorphism = p[Keys.glass] ?: d.useGlassmorphism,
            blurRadiusDp = p[Keys.blurRadius] ?: d.blurRadiusDp,
            gridColumns = p[Keys.gridColumns] ?: d.gridColumns,
            showAlbumArtInList = p[Keys.showArt] ?: d.showAlbumArtInList,
            animatedAlbumArt = p[Keys.animatedArt] ?: d.animatedAlbumArt,
            navigationStyle = p[Keys.navStyle]?.let { enumOrNull<NavigationStyle>(it) } ?: d.navigationStyle,
            playerStyle = p[Keys.playerStyle]?.let { enumOrNull<PlayerStyle>(it) } ?: d.playerStyle,
            enabledLibraryTabs = p[Keys.tabs] ?: d.enabledLibraryTabs,
            gaplessPlayback = p[Keys.gapless] ?: d.gaplessPlayback,
            crossfadeSeconds = p[Keys.crossfade] ?: d.crossfadeSeconds,
            replayGainMode = p[Keys.replayGain]?.let { enumOrNull<ReplayGainMode>(it) } ?: d.replayGainMode,
            preAmpDb = p[Keys.preAmp] ?: d.preAmpDb,
            playbackSpeed = p[Keys.speed] ?: d.playbackSpeed,
            pitchSemitones = p[Keys.pitch] ?: d.pitchSemitones,
            balance = p[Keys.balance] ?: d.balance,
            monoOutput = p[Keys.mono] ?: d.monoOutput,
            resumeOnHeadsetConnect = p[Keys.resumeOnConnect] ?: d.resumeOnHeadsetConnect,
            pauseOnHeadsetDisconnect = p[Keys.pauseOnDisconnect] ?: d.pauseOnHeadsetDisconnect,
            skipSilence = p[Keys.skipSilence] ?: d.skipSilence,
            audioOffload = p[Keys.offload] ?: d.audioOffload,
            bitPerfect = p[Keys.bitPerfect] ?: d.bitPerfect,
            preferUsbDac = p[Keys.usbDac] ?: d.preferUsbDac,
            sleepFadeOutSeconds = p[Keys.sleepFade] ?: d.sleepFadeOutSeconds,
            equalizerEnabled = p[Keys.eqEnabled] ?: d.equalizerEnabled,
            equalizerPreset = p[Keys.eqPreset] ?: d.equalizerPreset,
            equalizerBands = p[Keys.eqBands] ?: d.equalizerBands,
            bassBoostStrength = p[Keys.bass] ?: d.bassBoostStrength,
            virtualizerStrength = p[Keys.virtualizer] ?: d.virtualizerStrength,
            loudnessGainMb = p[Keys.loudness] ?: d.loudnessGainMb,
            ignoreShortTracksSeconds = p[Keys.minDuration] ?: d.ignoreShortTracksSeconds,
            respectNoMedia = p[Keys.respectNoMedia] ?: d.respectNoMedia,
            hiddenFolders = p[Keys.hiddenFolders] ?: d.hiddenFolders,
            autoScanOnStart = p[Keys.autoScan] ?: d.autoScanOnStart,
            downloadArtwork = p[Keys.downloadArtwork] ?: d.downloadArtwork,
            downloadLyrics = p[Keys.downloadLyrics] ?: d.downloadLyrics,
            swipeLeftAction = p[Keys.swipeLeft] ?: d.swipeLeftAction,
            swipeRightAction = p[Keys.swipeRight] ?: d.swipeRightAction,
            doubleTapAction = p[Keys.doubleTap] ?: d.doubleTapAction,
            volumeGestureEnabled = p[Keys.volumeGesture] ?: d.volumeGestureEnabled,
            brightnessGestureEnabled = p[Keys.brightnessGesture] ?: d.brightnessGestureEnabled,
            visualizerStyle = p[Keys.visualizer]?.let { enumOrNull<VisualizerStyle>(it) } ?: d.visualizerStyle,
            floatingLyricsEnabled = p[Keys.floatingLyrics] ?: d.floatingLyricsEnabled,
            lyricsAutoScroll = p[Keys.lyricsAutoScroll] ?: d.lyricsAutoScroll,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = put(Keys.themeMode, mode.name)
    suspend fun setDynamicColor(enabled: Boolean) = put(Keys.dynamicColor, enabled)
    suspend fun setAccentColor(color: Int) = put(Keys.accentColor, color)
    suspend fun setFont(font: AppFont) = put(Keys.font, font.name)
    suspend fun setCornerRadius(dp: Int) = put(Keys.cornerRadius, dp.coerceIn(0, 36))
    suspend fun setGlassmorphism(enabled: Boolean) = put(Keys.glass, enabled)
    suspend fun setBlurRadius(dp: Int) = put(Keys.blurRadius, dp.coerceIn(0, 64))
    suspend fun setGridColumns(columns: Int) = put(Keys.gridColumns, columns.coerceIn(1, 6))
    suspend fun setNavigationStyle(style: NavigationStyle) = put(Keys.navStyle, style.name)
    suspend fun setPlayerStyle(style: PlayerStyle) = put(Keys.playerStyle, style.name)
    suspend fun setEnabledTabs(tabs: Set<String>) = put(Keys.tabs, tabs)
    suspend fun setGapless(enabled: Boolean) = put(Keys.gapless, enabled)
    suspend fun setCrossfade(seconds: Int) = put(Keys.crossfade, seconds.coerceIn(0, 12))
    suspend fun setReplayGain(mode: ReplayGainMode) = put(Keys.replayGain, mode.name)
    suspend fun setPreAmp(db: Float) = put(Keys.preAmp, db.coerceIn(-15f, 15f))
    suspend fun setPlaybackSpeed(speed: Float) = put(Keys.speed, speed.coerceIn(0.25f, 4f))
    suspend fun setPitch(semitones: Float) = put(Keys.pitch, semitones.coerceIn(-12f, 12f))
    suspend fun setBalance(balance: Float) = put(Keys.balance, balance.coerceIn(-1f, 1f))
    suspend fun setMono(enabled: Boolean) = put(Keys.mono, enabled)
    suspend fun setSkipSilence(enabled: Boolean) = put(Keys.skipSilence, enabled)
    suspend fun setBitPerfect(enabled: Boolean) = put(Keys.bitPerfect, enabled)
    suspend fun setEqualizerEnabled(enabled: Boolean) = put(Keys.eqEnabled, enabled)
    suspend fun setEqualizerPreset(preset: String) = put(Keys.eqPreset, preset)
    suspend fun setEqualizerBands(csv: String) = put(Keys.eqBands, csv)
    suspend fun setBassBoost(strength: Int) = put(Keys.bass, strength.coerceIn(0, 1000))
    suspend fun setVirtualizer(strength: Int) = put(Keys.virtualizer, strength.coerceIn(0, 1000))
    suspend fun setLoudnessGain(millibels: Int) = put(Keys.loudness, millibels.coerceIn(0, 2000))
    suspend fun setHiddenFolders(paths: Set<String>) = put(Keys.hiddenFolders, paths)
    suspend fun setVisualizer(style: VisualizerStyle) = put(Keys.visualizer, style.name)
    suspend fun setFloatingLyrics(enabled: Boolean) = put(Keys.floatingLyrics, enabled)
    suspend fun setSwipeLeftAction(action: String) = put(Keys.swipeLeft, action)
    suspend fun setSwipeRightAction(action: String) = put(Keys.swipeRight, action)

    /** Serialises every preference for the backup/export flow. */
    suspend fun exportJson(): String {
        val prefs = context.dataStore.data.map { it.asMap() }
        val flat = mutableMapOf<String, String>()
        prefs.collectFirst { map -> map.forEach { (k, v) -> flat[k.name] = v.toString() } }
        return Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), flat.toMap())
    }

    // Convenience aliases used by the settings UI.
    suspend fun setGlassEffects(enabled: Boolean) = setGlassmorphism(enabled)
    suspend fun setCrossfadeSeconds(seconds: Int) = setCrossfade(seconds)
    suspend fun setReplayGainEnabled(enabled: Boolean) =
        setReplayGain(if (enabled) ReplayGainMode.TRACK else ReplayGainMode.OFF)
    suspend fun setMonoAudio(enabled: Boolean) = setMono(enabled)
    suspend fun setResumeOnHeadset(enabled: Boolean) = put(Keys.resumeOnConnect, enabled)
    suspend fun setRespectNoMedia(enabled: Boolean) = put(Keys.respectNoMedia, enabled)
    suspend fun setMinTrackSeconds(seconds: Int) = put(Keys.minDuration, seconds.coerceIn(0, 300))

    /**
     * Restores a payload produced by [exportJson].
     *
     * Export flattens every preference to its string form, so the concrete
     * preference type is inferred back from the text. Unparseable or unknown
     * entries are skipped rather than aborting the whole restore.
     */
    suspend fun importJson(json: String) {
        val flat = runCatching {
            Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), json)
        }.getOrNull() ?: return
        context.dataStore.edit { prefs ->
            flat.forEach { (name, raw) ->
                when {
                    raw == "true" || raw == "false" ->
                        prefs[booleanPreferencesKey(name)] = raw.toBoolean()

                    raw.startsWith("[") && raw.endsWith("]") ->
                        prefs[stringSetPreferencesKey(name)] = raw.removeSurrounding("[", "]")
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .toSet()

                    raw.toIntOrNull() != null -> prefs[intPreferencesKey(name)] = raw.toInt()
                    raw.toFloatOrNull() != null -> prefs[floatPreferencesKey(name)] = raw.toFloat()
                    else -> prefs[stringPreferencesKey(name)] = raw
                }
            }
        }
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }
}

/** Collects the first emission of a flow without pulling in extra dependencies. */
private suspend fun <T> Flow<T>.collectFirst(action: (T) -> Unit) {
    var taken = false
    collect { value ->
        if (!taken) {
            taken = true
            action(value)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)
}
