package com.neonbeat.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neonbeat.core.designsystem.R
import com.neonbeat.core.model.Song

/**
 * The single list row used everywhere songs are shown.
 *
 * Behaviour:
 * - Swipe end-to-start adds the track to the queue.
 * - Swipe start-to-end toggles favourite.
 * - Long press starts multi-select.
 *
 * Both swipe directions are non-destructive by default, so an accidental
 * gesture never deletes a file; destructive deletion is an explicit menu action.
 *
 * Accessibility: the whole row exposes a single merged description, and the
 * swipe actions are duplicated in the long-press menu so they remain reachable
 * with TalkBack and switch access.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    showArtwork: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
) {
    val dismissState = rememberSwipeToDismissBoxState(
        // Never actually remove the item; the gesture triggers an action instead.
        confirmValueChange = { false },
        positionalThreshold = { distance -> distance * 0.35f },
    )

    LaunchedEffect(dismissState.targetValue) {
        when (dismissState.targetValue) {
            SwipeToDismissBoxValue.EndToStart -> onAddToQueue()
            SwipeToDismissBoxValue.StartToEnd -> onToggleFavorite()
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    val rowDescription = buildString {
        append(song.title)
        append(", ")
        append(song.artist)
        if (isPlaying) {
            append(", ")
            append(stringResource(R.string.state_now_playing))
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { SwipeBackground(dismissState.dismissDirection) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else onClick() },
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clearAndSetSemantics { contentDescription = rowDescription },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
            }
            if (showArtwork) {
                AlbumArtwork(
                    artworkUri = song.artworkUri,
                    modifier = Modifier.size(52.dp),
                    contentDescription = null,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${song.artist} \u00b7 ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (song.isHiRes) HiResBadge()
            Text(
                text = song.durationMs.formatDuration(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val isQueue = direction == SwipeToDismissBoxValue.EndToStart
    val color = if (isQueue) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val scale by animateFloatAsState(
        targetValue = if (direction == SwipeToDismissBoxValue.Settled) 0.7f else 1f,
        label = "swipeIconScale",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (direction == SwipeToDismissBoxValue.Settled) Color.Transparent else color)
            .padding(horizontal = 24.dp),
        contentAlignment = if (isQueue) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Icon(
            imageVector = if (isQueue) Icons.AutoMirrored.Filled.QueueMusic else Icons.Default.Favorite,
            contentDescription = null,
            modifier = Modifier.scale(scale),
        )
    }
}

/** Formats a duration as `m:ss`, or `h:mm:ss` for long tracks. */
fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
