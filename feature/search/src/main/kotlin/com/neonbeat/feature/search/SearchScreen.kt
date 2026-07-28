package com.neonbeat.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonbeat.core.designsystem.component.SongRow
import com.neonbeat.core.model.Song

/**
 * Instant search screen.
 *
 * Results are grouped by entity type and rendered in one `LazyColumn` so the
 * whole page scrolls as a single surface. Each group is capped by the
 * repository, keeping the list short enough to render in a single frame even
 * while the user is still typing.
 */
@Composable
fun SearchScreen(
    onSongClick: (Song, Int) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("Songs, albums, artists, folders") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clear) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (results.songs.isNotEmpty()) {
                item(key = "songs-header") { SectionHeader("Songs") }
                items(
                    count = results.songs.size,
                    key = { "song-${results.songs[it].id}" },
                    contentType = { "song" },
                ) { index ->
                    val song = results.songs[index]
                    SongRow(
                        song = song,
                        isPlaying = false,
                        onClick = { onSongClick(song, index) },
                    )
                }
            }

            if (results.albums.isNotEmpty()) {
                item(key = "albums-header") { SectionHeader("Albums") }
                items(
                    count = results.albums.size,
                    key = { "album-${results.albums[it].id}" },
                ) { index ->
                    val album = results.albums[index]
                    ResultRow(album.title, album.artist) { onAlbumClick(album.id) }
                }
            }

            if (results.artists.isNotEmpty()) {
                item(key = "artists-header") { SectionHeader("Artists") }
                items(
                    count = results.artists.size,
                    key = { "artist-${results.artists[it].id}" },
                ) { index ->
                    val artist = results.artists[index]
                    ResultRow(artist.name, "${artist.songCount} songs") { onArtistClick(artist.id) }
                }
            }

            if (results.genres.isNotEmpty()) {
                item(key = "genres-header") { SectionHeader("Genres") }
                items(count = results.genres.size, key = { "genre-${results.genres[it].id}" }) { index ->
                    val genre = results.genres[index]
                    ResultRow(genre.name, "${genre.songCount} songs") {}
                }
            }

            if (results.folders.isNotEmpty()) {
                item(key = "folders-header") { SectionHeader("Folders") }
                items(count = results.folders.size, key = { "folder-${results.folders[it].path}" }) { index ->
                    val folder = results.folders[index]
                    ResultRow(folder.name, folder.path) {}
                }
            }

            if (results.playlists.isNotEmpty()) {
                item(key = "playlists-header") { SectionHeader("Playlists") }
                items(count = results.playlists.size, key = { "playlist-${results.playlists[it].id}" }) { index ->
                    val playlist = results.playlists[index]
                    ResultRow(playlist.name, "${playlist.songCount} songs") {}
                }
            }

            if (query.isNotBlank() && results.isEmpty) {
                item(key = "empty") {
                    Text(
                        text = "No matches for \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
