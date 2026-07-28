package com.neonbeat.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.neonbeat.core.designsystem.component.AlbumArtwork
import com.neonbeat.core.designsystem.component.SongRow
import com.neonbeat.core.model.Song

/**
 * Main library screen: tabbed browsing over songs, albums, artists, genres and folders.
 *
 * Rendering rules that keep scrolling stable at 120 Hz on 100k-item lists:
 * - Rows come from Paging 3 with stable keys, so recomposition is scoped to the
 *   items that actually changed.
 * - Placeholders are enabled, so the scrollbar never jumps while pages load.
 * - `contentType` is supplied so Compose reuses the right row layouts.
 *
 * @param onSongClick Receives the tapped song and its index in the current list.
 * @param contentPadding Insets from the scaffold (edge-to-edge plus mini player).
 * @param gridColumns Column count for album/artist grids; adaptive layouts raise
 *   this on tablets and unfolded foldables.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onSongClick: (Song, Int) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    gridColumns: Int = 2,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val songs = viewModel.songs.collectAsLazyPagingItems()
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val artists = viewModel.artists.collectAsLazyPagingItems()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = uiState.selectedTab.ordinal,
            edgePadding = 12.dp,
        ) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(tab.label()) },
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = uiState.isScanning,
            onRefresh = { viewModel.rescan(force = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            when (uiState.selectedTab) {
                LibraryTab.SONGS -> SongList(
                    songs = songs,
                    contentPadding = contentPadding,
                    selectionMode = uiState.selectionMode,
                    selectedIds = uiState.selectedSongIds,
                    onClick = onSongClick,
                    onLongClick = viewModel::toggleSelection,
                    onFavorite = viewModel::favorite,
                )

                LibraryTab.ALBUMS -> LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    items(
                        count = albums.itemCount,
                        key = { index -> albums.peek(index)?.id ?: index },
                        contentType = { "album" },
                    ) { index ->
                        albums[index]?.let { album ->
                            ArtworkTile(
                                title = album.title,
                                subtitle = album.artist,
                                artworkUri = album.artworkUri,
                                onClick = { onAlbumClick(album.id) },
                            )
                        }
                    }
                }

                LibraryTab.ARTISTS -> LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    items(
                        count = artists.itemCount,
                        key = { index -> artists.peek(index)?.id ?: index },
                        contentType = { "artist" },
                    ) { index ->
                        artists[index]?.let { artist ->
                            ArtworkTile(
                                title = artist.name,
                                subtitle = "${artist.songCount} songs",
                                artworkUri = artist.artworkUri,
                                onClick = { onArtistClick(artist.id) },
                            )
                        }
                    }
                }

                LibraryTab.GENRES -> LazyColumn(contentPadding = contentPadding) {
                    items(count = genres.size, key = { genres[it].id }) { index ->
                        val genre = genres[index]
                        SimpleRow(genre.name, "${genre.songCount} songs")
                    }
                }

                LibraryTab.FOLDERS -> LazyColumn(contentPadding = contentPadding) {
                    items(count = folders.size, key = { folders[it].path }) { index ->
                        val folder = folders[index]
                        SimpleRow(
                            title = folder.name,
                            subtitle = "${folder.songCount} songs \u00b7 ${folder.path}",
                        )
                    }
                }

                LibraryTab.PLAYLISTS -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Playlists", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (uiState.songCount == 0 && !uiState.isScanning) {
                EmptyLibrary()
            }
        }
    }
}

@Composable
private fun SongList(
    songs: LazyPagingItems<Song>,
    contentPadding: PaddingValues,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onClick: (Song, Int) -> Unit,
    onLongClick: (Long) -> Unit,
    onFavorite: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            count = songs.itemCount,
            key = { index -> songs.peek(index)?.id ?: index },
            contentType = { "song" },
        ) { index ->
            val song = songs[index]
            if (song != null) {
                SongRow(
                    song = song,
                    isPlaying = false,
                    isSelected = song.id in selectedIds,
                    selectionMode = selectionMode,
                    onClick = { onClick(song, index) },
                    onLongClick = { onLongClick(song.id) },
                    onToggleSelect = { onLongClick(song.id) },
                    onToggleFavorite = { onFavorite(song.id) },
                )
            } else {
                SongRowPlaceholder()
            }
        }
    }
}

@Composable
private fun ArtworkTile(
    title: String,
    subtitle: String,
    artworkUri: String?,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        AlbumArtwork(
            artworkUri = artworkUri,
            contentDescription = title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onClick),
        )
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SimpleRow(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Fixed-height stand-in shown while a page is still loading. */
@Composable
private fun SongRowPlaceholder() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
    )
}

@Composable
private fun EmptyLibrary() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No music yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add audio files to this device, then pull down to rescan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LibraryTab.label(): String = when (this) {
    LibraryTab.SONGS -> "Songs"
    LibraryTab.ALBUMS -> "Albums"
    LibraryTab.ARTISTS -> "Artists"
    LibraryTab.GENRES -> "Genres"
    LibraryTab.FOLDERS -> "Folders"
    LibraryTab.PLAYLISTS -> "Playlists"
}
