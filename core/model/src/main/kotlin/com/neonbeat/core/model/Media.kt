package com.neonbeat.core.model

import kotlinx.serialization.Serializable

/**
 * Immutable domain representation of a single audio track.
 *
 * The model is intentionally flat and primitive-heavy so it can be projected
 * straight out of Room with zero mapping cost, which matters when paging
 * through libraries of 100k+ items.
 *
 * @property id Stable MediaStore id, reused as the Room primary key.
 * @property uri `content://` URI used by ExoPlayer for playback.
 * @property data Absolute file path, used for folder browsing and tag editing.
 * @property replayGainTrack Track gain in dB parsed from tags, or `null`.
 * @property replayGainAlbum Album gain in dB parsed from tags, or `null`.
 */
@Serializable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val albumArtist: String?,
    val genre: String?,
    val composer: String?,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    val mimeType: String,
    val uri: String,
    val data: String,
    val folderPath: String,
    val dateAddedSeconds: Long,
    val dateModifiedSeconds: Long,
    val replayGainTrack: Float? = null,
    val replayGainAlbum: Float? = null,
    val hasEmbeddedLyrics: Boolean = false,
    val artworkUri: String? = null,
) {
    /** True for formats that can be decoded losslessly (used by the Hi-Res badge). */
    val isLossless: Boolean
        get() = AudioFormat.fromMimeType(mimeType)?.lossless == true

    /** Hi-Res Audio is commonly defined as >44.1 kHz sample rate on a lossless codec. */
    val isHiRes: Boolean
        get() = isLossless && sampleRate > 44_100
}

/** Every container/codec pair the scanner accepts. */
enum class AudioFormat(val extension: String, val mimeTypes: List<String>, val lossless: Boolean) {
    MP3("mp3", listOf("audio/mpeg", "audio/mp3"), false),
    AAC("aac", listOf("audio/aac", "audio/aacp"), false),
    M4A("m4a", listOf("audio/mp4", "audio/m4a", "audio/x-m4a"), false),
    ALAC("m4a", listOf("audio/alac", "audio/x-alac"), true),
    FLAC("flac", listOf("audio/flac", "audio/x-flac"), true),
    WAV("wav", listOf("audio/wav", "audio/x-wav", "audio/vnd.wave"), true),
    OGG("ogg", listOf("audio/ogg", "application/ogg", "audio/vorbis"), false),
    OPUS("opus", listOf("audio/opus", "audio/x-opus+ogg"), false),
    ;

    companion object {
        private val byMime: Map<String, AudioFormat> =
            entries.flatMap { format -> format.mimeTypes.map { it to format } }.toMap()

        fun fromMimeType(mimeType: String): AudioFormat? = byMime[mimeType.lowercase()]

        /** MediaStore selection fragment restricting the scan to supported types. */
        val supportedMimeTypes: List<String> = entries.flatMap { it.mimeTypes }.distinct()
    }
}

@Serializable
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val year: Int,
    val songCount: Int,
    val durationMs: Long,
    val artworkUri: String?,
    val dateAddedSeconds: Long,
)

@Serializable
data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val durationMs: Long,
    val artworkUri: String?,
)

@Serializable
data class Genre(
    val id: Long,
    val name: String,
    val songCount: Int,
)

/**
 * A node in the folder browser tree.
 *
 * @property isHidden Set when the user hid the folder manually.
 * @property hasNoMedia Set when a `.nomedia` marker exists in the directory.
 */
@Serializable
data class MusicFolder(
    val path: String,
    val name: String,
    val songCount: Int,
    val subfolderCount: Int,
    val isHidden: Boolean = false,
    val hasNoMedia: Boolean = false,
)

@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val durationMs: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val artworkUri: String? = null,
    val kind: PlaylistKind = PlaylistKind.USER,
    /** Serialized [SmartPlaylistRule] set, only present when [kind] is SMART. */
    val smartRules: String? = null,
    /** Shared-editing metadata placeholder for the collaborative playlist structure. */
    val collaborators: List<String> = emptyList(),
)

enum class PlaylistKind { USER, SMART, AUTO }

/** Declarative rule used to build smart playlists without hand-written SQL. */
@Serializable
data class SmartPlaylistRule(
    val field: SmartField,
    val operator: SmartOperator,
    val value: String,
)

enum class SmartField {
    TITLE,
    ARTIST,
    ALBUM,
    GENRE,
    YEAR,
    PLAY_COUNT,
    RATING,
    DATE_ADDED,
    LAST_PLAYED,
    DURATION,
    FAVORITE,
    FOLDER,
}

enum class SmartOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    STARTS_WITH,
    GREATER_THAN,
    LESS_THAN,
    IN_LAST_DAYS,
}

/** Built-in, always-available auto playlists. */
enum class AutoPlaylist(val key: String) {
    RECENTLY_ADDED("recently_added"),
    RECENTLY_PLAYED("recently_played"),
    MOST_PLAYED("most_played"),
    FAVORITES("favorites"),
    NEVER_PLAYED("never_played"),
    SHUFFLE_ALL("shuffle_all"),
}

/** A single timestamped listen, used for statistics and recommendations. */
@Serializable
data class PlayEvent(
    val songId: Long,
    val playedAtEpochMs: Long,
    val listenedMs: Long,
    val completed: Boolean,
)

/** Position bookmark inside a long track (audiobooks, DJ sets, live recordings). */
@Serializable
data class Bookmark(
    val id: Long,
    val songId: Long,
    val positionMs: Long,
    val label: String,
)

/** One lyric line; [timeMs] is `null` for unsynced lyrics. */
@Serializable
data class LyricLine(val timeMs: Long?, val text: String)

@Serializable
data class Lyrics(
    val songId: Long,
    val lines: List<LyricLine>,
    val source: LyricsSource,
) {
    val isSynced: Boolean get() = lines.any { it.timeMs != null }
}

enum class LyricsSource { EMBEDDED, LRC_FILE, DOWNLOADED, MANUAL }

/** Remote/local source a song can be streamed from. */
@Serializable
data class MediaSourceLocation(
    val id: String,
    val displayName: String,
    val type: SourceType,
    val rootUri: String,
    val username: String? = null,
)

enum class SourceType { LOCAL, SMB, FTP, WEBDAV, DLNA, NAS }
