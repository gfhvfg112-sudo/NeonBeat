package com.neonbeat.data.mapper

import com.neonbeat.core.database.entity.PlaylistSongEntity
import com.neonbeat.core.database.entity.SongEntity
import com.neonbeat.core.database.model.AlbumAggregate
import com.neonbeat.core.database.model.ArtistAggregate
import com.neonbeat.core.database.model.FolderAggregate
import com.neonbeat.core.database.model.GenreAggregate
import com.neonbeat.core.database.model.PlaylistAggregate
import com.neonbeat.core.model.Album
import com.neonbeat.core.model.Artist
import com.neonbeat.core.model.Genre
import com.neonbeat.core.model.MusicFolder
import com.neonbeat.core.model.Playlist
import com.neonbeat.core.model.PlaylistKind
import com.neonbeat.core.model.Song

/**
 * Database <-> domain conversions.
 *
 * Kept as extension functions in one file so the mapping cost is obvious and
 * every projection stays in sync with the model definitions.
 */
fun SongEntity.toSong(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    albumArtist = albumArtist,
    genre = genre,
    composer = composer,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    bitrate = bitrate,
    sampleRate = sampleRate,
    channels = channels,
    mimeType = mimeType,
    uri = uri,
    data = data,
    folderPath = folderPath,
    dateAddedSeconds = dateAddedSeconds,
    dateModifiedSeconds = dateModifiedSeconds,
    replayGainTrack = replayGainTrack,
    replayGainAlbum = replayGainAlbum,
    hasEmbeddedLyrics = hasEmbeddedLyrics,
    artworkUri = artworkUri,
)

fun AlbumAggregate.toAlbum(): Album = Album(
    id = id,
    title = title,
    artist = artist,
    artistId = artistId,
    year = year,
    songCount = songCount,
    durationMs = durationMs,
    artworkUri = artworkUri,
    dateAddedSeconds = dateAddedSeconds,
)

fun ArtistAggregate.toArtist(): Artist = Artist(
    id = id,
    name = name,
    albumCount = albumCount,
    songCount = songCount,
    durationMs = durationMs,
    artworkUri = artworkUri,
)

fun GenreAggregate.toGenre(): Genre = Genre(
    id = name.hashCode().toLong(),
    name = name,
    songCount = songCount,
)

fun FolderAggregate.toFolder(): MusicFolder = MusicFolder(
    path = path,
    name = path.substringAfterLast('/').ifBlank { path },
    songCount = songCount,
    subfolderCount = 0,
    isHidden = isHidden,
    hasNoMedia = hasNoMedia,
)

fun PlaylistAggregate.toPlaylist(): Playlist = Playlist(
    id = id,
    name = name,
    songCount = songCount,
    durationMs = durationMs,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    artworkUri = artworkUri,
    kind = runCatching { PlaylistKind.valueOf(kind) }.getOrDefault(PlaylistKind.USER),
    smartRules = smartRules,
)

fun playlistEntry(playlistId: Long, songId: Long, position: Int, now: Long) =
    PlaylistSongEntity(playlistId, songId, position, now)
