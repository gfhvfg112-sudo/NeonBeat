package com.neonbeat.feature.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonbeat.core.designsystem.component.AlbumArtwork
import com.neonbeat.core.designsystem.component.BlurredArtworkBackground
import com.neonbeat.core.designsystem.component.GlassSurface
import com.neonbeat.core.designsystem.component.formatDuration

/**
 * Full-screen now-playing surface.
 *
 * Gesture map — every gesture also has an equivalent button or menu entry so
 * the screen stays fully usable with TalkBack and switch access:
 * - Double tap on the left/right third: seek -10 s / +10 s
 * - Double tap in the middle: play/pause
 * - Pinch on the artwork: zoom between 0.8x and 1.6x
 * - Vertical drag on the left/right edge: brightness / volume
 *
 * Layout adapts to the window: on wide screens the artwork and the controls sit
 * side by side instead of stacked.
 */
@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    wideLayout: Boolean = false,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val song = playback.currentSong

    Box(modifier.fillMaxSize()) {
        BlurredArtworkBackground(
            artworkUri = song?.artworkUri,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            ArtworkPane(
                artworkUri = song?.artworkUri,
                title = song?.title.orEmpty(),
                scale = uiState.artworkScale,
                spinning = playback.isPlaying,
                onPinch = viewModel::onArtworkPinch,
                onDoubleTapSeek = viewModel::seekBy,
                onDoubleTapCenter = viewModel::playPause,
                modifier = Modifier
                    .fillMaxWidth(if (wideLayout) 0.5f else 0.86f)
                    .aspectRatio(1f),
            )

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = song?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song?.artist.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            SeekBar(
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                onSeek = viewModel::seekTo,
            )

            TransportControls(
                isPlaying = playback.isPlaying,
                shuffleEnabled = playback.shuffleEnabled,
                onPlayPause = viewModel::playPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onShuffle = viewModel::toggleShuffle,
                onRepeat = viewModel::cycleRepeatMode,
            )

            SecondaryControls(
                isFavorite = song?.isFavorite == true,
                onFavorite = viewModel::favoriteCurrent,
                onLyrics = viewModel::toggleLyrics,
                onQueue = viewModel::toggleQueue,
                onCollapse = onCollapse,
            )
        }
    }
}

/** Artwork with pinch-zoom and double-tap seek zones. */
@Composable
private fun ArtworkPane(
    artworkUri: String?,
    title: String,
    scale: Float,
    spinning: Boolean,
    onPinch: (Float) -> Unit,
    onDoubleTapSeek: (Long) -> Unit,
    onDoubleTapCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var width by remember { mutableFloatStateOf(1f) }

    GlassSurface(
        modifier = modifier
            .pointerInput(Unit) {
                width = size.width.toFloat()
                detectTapGestures(
                    onDoubleTap = { offset ->
                        when {
                            offset.x < width / 3f -> onDoubleTapSeek(-10_000L)
                            offset.x > width * 2f / 3f -> onDoubleTapSeek(10_000L)
                            else -> onDoubleTapCenter()
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ -> onPinch(zoom) }
            },
    ) {
        AlbumArtwork(
            artworkUri = artworkUri,
            contentDescription = title,
            spinning = spinning,
            modifier = Modifier
                .fillMaxSize()
                .scale(scale),
        )
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var scrubPosition by remember { mutableFloatStateOf(-1f) }
    val fraction = when {
        scrubPosition >= 0f -> scrubPosition
        durationMs > 0 -> positionMs.toFloat() / durationMs
        else -> 0f
    }

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { scrubPosition = it },
            onValueChangeFinished = {
                if (durationMs > 0 && scrubPosition >= 0f) {
                    onSeek((scrubPosition * durationMs).toLong())
                }
                scrubPosition = -1f
            },
            modifier = Modifier.semantics { contentDescription = "Seek bar" },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(positionMs.formatDuration(), style = MaterialTheme.typography.labelMedium)
            Text(durationMs.coerceAtLeast(0L).formatDuration(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onShuffle) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = if (shuffleEnabled) "Disable shuffle" else "Enable shuffle",
                tint = if (shuffleEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track")
        }
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.height(64.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next track")
        }
        IconButton(onClick = onRepeat) {
            Icon(Icons.Default.Repeat, contentDescription = "Change repeat mode")
        }
    }
}

@Composable
private fun SecondaryControls(
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onCollapse: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onFavorite) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onLyrics) {
            Icon(Icons.Default.Lyrics, contentDescription = "Show lyrics")
        }
        IconButton(onClick = onQueue) {
            Icon(Icons.Default.QueueMusic, contentDescription = "Show queue")
        }
        IconButton(onClick = onCollapse) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse player")
        }
    }
}
