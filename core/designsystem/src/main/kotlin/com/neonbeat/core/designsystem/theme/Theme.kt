package com.neonbeat.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Fallback brand palette used when Material You dynamic color is unavailable. */
private val NeonPrimary = Color(0xFF66E0FF)
private val NeonSecondary = Color(0xFFB388FF)
private val NeonTertiary = Color(0xFFFF7AC6)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00629A),
    secondary = Color(0xFF6750A4),
    tertiary = Color(0xFFB1266F),
    background = Color(0xFFFBFCFF),
    surface = Color(0xFFFBFCFF),
)

private val DarkColors = darkColorScheme(
    primary = NeonPrimary,
    secondary = NeonSecondary,
    tertiary = NeonTertiary,
    background = Color(0xFF0B0E12),
    surface = Color(0xFF0F131A),
    surfaceContainer = Color(0xFF161B22),
)

/**
 * True-black variant for OLED panels.
 *
 * Pure black pixels are switched off entirely on OLED, which measurably reduces
 * power draw during long listening sessions with the screen on.
 */
private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF070707),
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF121212),
)

/** Which theme variant the user selected. Mirrors the datastore enum. */
enum class NeonThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/** Extra design tokens Material 3 does not model. */
data class NeonTokens(
    val cornerRadiusDp: Int = 20,
    val glassEnabled: Boolean = true,
    val blurRadiusDp: Int = 32,
    val isAmoled: Boolean = false,
)

val LocalNeonTokens = staticCompositionLocalOf { NeonTokens() }

/**
 * Root theme for every NeonBeat surface.
 *
 * @param mode Light/dark/AMOLED selection.
 * @param dynamicColor Use the wallpaper-derived Material You palette (API 31+).
 * @param accentColor Fallback seed color when dynamic color is off.
 * @param cornerRadiusDp User-tunable roundness applied to all shape tokens.
 */
@Composable
fun NeonBeatTheme(
    mode: NeonThemeMode = NeonThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accentColor: Color = NeonPrimary,
    cornerRadiusDp: Int = 20,
    glassEnabled: Boolean = true,
    blurRadiusDp: Int = 32,
    typography: Typography = NeonTypography,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        NeonThemeMode.SYSTEM -> systemDark
        NeonThemeMode.LIGHT -> false
        NeonThemeMode.DARK, NeonThemeMode.AMOLED -> true
    }
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme: ColorScheme = remember(mode, dynamicColor, accentColor, dark) {
        val base = when {
            dynamicColor && supportsDynamic && dark -> dynamicDarkColorScheme(context)
            dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
            dark -> DarkColors.copy(primary = accentColor)
            else -> LightColors.copy(primary = accentColor)
        }
        if (mode == NeonThemeMode.AMOLED) {
            base.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = AmoledColors.surfaceContainerLow,
                surfaceContainer = AmoledColors.surfaceContainer,
                surfaceContainerHigh = AmoledColors.surfaceContainerHigh,
            )
        } else {
            base
        }
    }

    val shapes = remember(cornerRadiusDp) {
        val r = cornerRadiusDp.coerceIn(0, 36)
        Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape((r / 4).dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape((r / 2).dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(r.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape((r * 1.4f).dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape((r * 2f).dp),
        )
    }

    CompositionLocalProvider(
        LocalNeonTokens provides NeonTokens(
            cornerRadiusDp = cornerRadiusDp,
            glassEnabled = glassEnabled,
            blurRadiusDp = blurRadiusDp,
            isAmoled = mode == NeonThemeMode.AMOLED,
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}
