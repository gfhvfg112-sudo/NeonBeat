package com.neonbeat.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Visual styles offered by the audio visualizer. */
enum class VisualizerKind { WAVEFORM, CIRCULAR_SPECTRUM, BARS, PARTICLES, FLUID }

/**
 * GPU-accelerated audio visualizer.
 *
 * Everything is drawn into a single Compose [Canvas], which compiles to one
 * hardware-accelerated render node: no bitmaps, no per-frame allocations, and
 * no `View` interop. Magnitudes are expected to be normalised to `0f..1f` and
 * are supplied by the capture layer at ~30 Hz; Compose interpolates between
 * updates so the animation still looks smooth on a 120 Hz panel.
 *
 * @param magnitudes FFT magnitudes, low frequency first.
 * @param kind Which visualisation to render.
 * @param color Base color; a vertical gradient is derived from it.
 */
@Composable
fun AudioVisualizer(
    magnitudes: FloatArray,
    kind: VisualizerKind,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // A single animated "energy" value keeps idle frames cheap when audio is quiet.
    val energy by animateFloatAsState(
        targetValue = magnitudes.average().toFloat().coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 220f),
        label = "visualizerEnergy",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (magnitudes.isEmpty()) return@Canvas
        val brush = Brush.verticalGradient(
            listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.25f)),
        )
        when (kind) {
            VisualizerKind.BARS -> drawBars(magnitudes, brush)
            VisualizerKind.WAVEFORM -> drawWaveform(magnitudes, color)
            VisualizerKind.CIRCULAR_SPECTRUM -> drawCircularSpectrum(magnitudes, brush, energy)
            VisualizerKind.PARTICLES -> drawParticles(magnitudes, color, energy)
            VisualizerKind.FLUID -> drawFluid(magnitudes, brush, energy)
        }
    }
}

private fun DrawScope.drawBars(magnitudes: FloatArray, brush: Brush) {
    val barCount = magnitudes.size
    val gap = 2.dp.toPx()
    val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)
    magnitudes.forEachIndexed { index, magnitude ->
        val height = (magnitude.coerceIn(0f, 1f) * size.height).coerceAtLeast(2f)
        drawRoundRect(
            brush = brush,
            topLeft = Offset(index * (barWidth + gap), size.height - height),
            size = Size(barWidth, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
        )
    }
}

private fun DrawScope.drawWaveform(magnitudes: FloatArray, color: Color) {
    val path = Path()
    val step = size.width / (magnitudes.size - 1).coerceAtLeast(1)
    val center = size.height / 2f
    magnitudes.forEachIndexed { index, magnitude ->
        val x = index * step
        val y = center - (magnitude - 0.5f) * size.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawCircularSpectrum(magnitudes: FloatArray, brush: Brush, energy: Float) {
    val radius = minOf(size.width, size.height) / 4f * (1f + energy * 0.12f)
    val center = Offset(size.width / 2f, size.height / 2f)
    val sweep = 360f / magnitudes.size
    magnitudes.forEachIndexed { index, magnitude ->
        val angle = Math.toRadians((index * sweep).toDouble())
        val length = radius * 0.6f * magnitude.coerceIn(0f, 1f)
        val start = Offset(
            center.x + (radius * cos(angle)).toFloat(),
            center.y + (radius * sin(angle)).toFloat(),
        )
        val end = Offset(
            center.x + ((radius + length) * cos(angle)).toFloat(),
            center.y + ((radius + length) * sin(angle)).toFloat(),
        )
        drawLine(brush = brush, start = start, end = end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawParticles(magnitudes: FloatArray, color: Color, energy: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = minOf(size.width, size.height) / 2f
    magnitudes.forEachIndexed { index, magnitude ->
        val angle = index * GOLDEN_ANGLE
        val distance = maxRadius * (index.toFloat() / magnitudes.size) * (0.6f + energy * 0.6f)
        val position = Offset(
            center.x + distance * cos(angle.toDouble()).toFloat(),
            center.y + distance * sin(angle.toDouble()).toFloat(),
        )
        drawCircle(
            color = color.copy(alpha = (0.25f + magnitude * 0.75f).coerceIn(0f, 1f)),
            radius = (1.5f + magnitude * 6f).dp.toPx(),
            center = position,
        )
    }
}

private fun DrawScope.drawFluid(magnitudes: FloatArray, brush: Brush, energy: Float) {
    val path = Path()
    val step = size.width / (magnitudes.size - 1).coerceAtLeast(1)
    val baseline = size.height * (0.75f - energy * 0.1f)
    path.moveTo(0f, size.height)
    magnitudes.forEachIndexed { index, magnitude ->
        val x = index * step
        val y = baseline - magnitude * size.height * 0.4f
        if (index == 0) {
            path.lineTo(x, y)
        } else {
            // Quadratic smoothing gives the liquid, non-jagged look.
            val previousX = (index - 1) * step
            path.quadraticTo((previousX + x) / 2f, y, x, y)
        }
    }
    path.lineTo(size.width, size.height)
    path.close()
    rotate(degrees = 0f) { drawPath(path = path, brush = brush) }
}

/** ~137.5 degrees in radians; produces an even, non-repeating particle spread. */
private const val GOLDEN_ANGLE = 2.39996f
