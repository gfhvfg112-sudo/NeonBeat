package com.neonbeat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonbeat.core.datastore.ThemeMode
import com.neonbeat.core.designsystem.theme.NeonBeatTheme
import com.neonbeat.core.designsystem.theme.NeonThemeMode
import com.neonbeat.navigation.NeonBeatApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity hosting the whole Compose UI.
 *
 * Startup ordering matters for the sub-500 ms cold-start target:
 * 1. The splash screen is installed *before* `super.onCreate` so the system
 *    hands the first frame straight to us.
 * 2. The splash is kept on screen only until the theme preference resolves,
 *    which avoids a light-to-dark flash without blocking on the library scan.
 * 3. Edge-to-edge is enabled before `setContent` so insets are correct on the
 *    very first composition instead of causing a re-layout.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { !viewModel.isReady.value }
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            NeonBeatTheme(
                mode = settings.themeMode.toDesignSystemMode(),
                dynamicColor = settings.useDynamicColor,
                cornerRadiusDp = settings.cornerRadiusDp,
                glassEnabled = settings.useGlassmorphism,
                blurRadiusDp = settings.blurRadiusDp,
            ) {
                NeonBeatApp()
            }
        }
    }
}

private fun ThemeMode.toDesignSystemMode(): NeonThemeMode = when (this) {
    ThemeMode.SYSTEM -> NeonThemeMode.SYSTEM
    ThemeMode.LIGHT -> NeonThemeMode.LIGHT
    ThemeMode.DARK -> NeonThemeMode.DARK
    ThemeMode.AMOLED -> NeonThemeMode.AMOLED
}
