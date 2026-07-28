package com.neonbeat.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.neonbeat.core.designsystem.theme.LocalNeonTokens

/**
 * Frosted-glass container.
 *
 * Uses a translucent tint plus a hairline highlight border rather than a live
 * backdrop blur on every frame: blurring a full-screen backdrop each frame is
 * the single most expensive effect on mid-range GPUs. Pass a blurred image into
 * [BlurredArtworkBackground] behind this surface for the full glassmorphism look.
 *
 * @param alpha Tint opacity; lower values read as "more glassy".
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LocalNeonTokens.current.cornerRadiusDp.dp),
    alpha: Float = 0.28f,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalNeonTokens.current
    val scheme = MaterialTheme.colorScheme
    val base = if (tokens.glassEnabled) {
        Brush.verticalGradient(
            listOf(
                scheme.surfaceContainerHigh.copy(alpha = alpha + 0.08f),
                scheme.surfaceContainer.copy(alpha = alpha),
            ),
        )
    } else {
        Brush.verticalGradient(listOf(scheme.surfaceContainer, scheme.surfaceContainer))
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(base)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (tokens.glassEnabled) 0.18f else 0f),
                        Color.Transparent,
                    ),
                ),
                shape = shape,
            ),
        content = content,
    )
}

/**
 * Blurred album art used as the now-playing backdrop.
 *
 * The blur is applied by the render node ([Modifier.blur]), so it runs entirely
 * on the GPU and costs a single composited layer instead of a CPU bitmap pass.
 *
 * @param blurRadiusDp 0 disables the effect entirely.
 */
@Composable
fun BlurredArtworkBackground(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    blurRadiusDp: Int = LocalNeonTokens.current.blurRadiusDp,
    scrimAlpha: Float = 0.55f,
) {
    Box(modifier) {
        if (artworkUri != null && blurRadiusDp > 0) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUri)
                    // A downsampled source is plenty once blurred, and keeps memory low.
                    .size(160)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadiusDp.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha * 0.6f),
                            MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha),
                        ),
                    ),
                ),
        )
    }
}

/**
 * Album artwork with a graceful placeholder.
 *
 * @param spinning When true the art rotates slowly, used for the "animated
 *   album art" vinyl player style. The animation is driven by an infinite
 *   transition so it pauses automatically when the composable leaves the screen.
 */
@Composable
fun AlbumArtwork(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    contentDescription: String?,
    spinning: Boolean = false,
    shape: Shape = RoundedCornerShape(LocalNeonTokens.current.cornerRadiusDp.dp / 2),
) {
    val transition = rememberInfiniteTransition(label = "artworkSpin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (spinning) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "artworkAngle",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (spinning) Modifier.rotate(angle) else Modifier),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artworkUri)
                .crossfade(200)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Small "Hi-Res" chip shown for lossless tracks above 44.1 kHz. */
@Composable
fun HiResBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "Hi-Res",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
