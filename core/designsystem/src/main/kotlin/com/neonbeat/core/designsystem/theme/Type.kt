package com.neonbeat.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp

/**
 * Type scale for NeonBeat.
 *
 * `TextDirection.Content` is set on every style so Arabic, Hebrew and Persian
 * strings lay out right-to-left automatically without per-screen handling.
 */
private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
    family: FontFamily = FontFamily.Default,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp,
    textDirection = TextDirection.Content,
)

val NeonTypography = Typography(
    displayLarge = style(57, 64, FontWeight.SemiBold, (-0.25)),
    displayMedium = style(45, 52, FontWeight.SemiBold),
    displaySmall = style(36, 44, FontWeight.SemiBold),
    headlineLarge = style(32, 40, FontWeight.Bold),
    headlineMedium = style(28, 36, FontWeight.Bold),
    headlineSmall = style(24, 32, FontWeight.Bold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.15),
    titleSmall = style(14, 20, FontWeight.Medium, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.5),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.25),
    bodySmall = style(12, 16, FontWeight.Normal, 0.4),
    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.5),
    labelSmall = style(11, 16, FontWeight.Medium, 0.5),
)
