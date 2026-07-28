package com.neonbeat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonbeat.core.designsystem.component.AlbumArtwork
import com.neonbeat.core.designsystem.component.GlassSurface
import com.neonbeat.feature.player.PlayerViewModel

/**
 * Persistent mini player docked above the navigation bar.
 *
 * It is only composed when something is loaded, and it animates in and out so
 * list content is never covered by an empty bar. Tapping anywhere expands the
 * full now-playing screen; the play/pause and next buttons stay hit-testable
 * inside that tap area because they handle their own clicks first.
 */
@Composable
fun MiniPlayer(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val song by viewModel.currentSong.collectAsStateWithLifecycle()

    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = song != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            // Captured once so the row always renders one consistent snapshot,
            // even if the track changes mid-composition.
            val current = song ?: return@AnimatedVisibility

            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = bottomInset + 8.dp)
                    .clickable(onClick = onExpand),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AlbumArtwork(
                            artworkUri = current.artworkUri,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = current.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = current.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = viewModel::playPause) {
                            Icon(
                                imageVector = if (playback.isPlaying) {
                                    Icons.Default.Pause
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                            )
                        }
                        IconButton(onClick = viewModel::next) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next track")
                        }
                    }

                    LinearProgressIndicator(
                        progress = {
                            val duration = playback.durationMs
                            if (duration > 0) {
                                (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                    )
                }
            }
        }
    }
}
