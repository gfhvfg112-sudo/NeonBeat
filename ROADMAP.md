# Status and roadmap

An honest inventory of this codebase. Nothing here was compiled or run on a
device while it was being written, because the authoring environment had no
Android SDK, Gradle daemon or network access. The GitHub Actions workflow is the
first real compile; expect a few red runs before the first green one.

Legend: [x] implemented in code, [~] partially implemented / scaffolded seam,
[ ] not implemented (stub or absent).

## Library

- [x] Automatic MediaStore scan, batched at 500 rows per cursor page
- [x] MP3, FLAC, WAV, AAC, OGG, OPUS, M4A, ALAC (container support comes from
      Media3 extension renderers)
- [x] Folder browser, albums, artists, genres
- [x] Recently added, favorites, most played, recently played
- [x] Hidden folders and .nomedia handling
- [x] Duplicate finder and missing-artwork detector queries
- [x] Multi-select and batch operations in the library view model
- [~] Tag editing: writes MediaStore-visible columns. Full in-file tag rewriting
      (ID3v2 / Vorbis comments) needs jaudiotagger wiring; the dependency is in
      the version catalog but the writer is not implemented.

## Playback

- [x] Gapless playback, audio focus, becoming-noisy, wake mode
- [x] Equalizer, bass boost, virtualizer, loudness enhancer
- [x] Sleep timer with equal-power fade-out
- [x] Playback speed, pitch, balance, mono
- [x] MediaSession, notification, lock screen, headset and Bluetooth controls
- [x] Queue persistence and restore
- [x] A-B repeat, smart shuffle
- [~] Crossfade: controller and settings exist; true overlapping crossfade needs
      a dual-player or custom AudioProcessor implementation
- [~] ReplayGain: settings and gain plumbing exist; tag parsing for
      REPLAYGAIN_TRACK_GAIN is not implemented
- [~] Android Auto: browse tree and automotive descriptor are present, untested
      against the Auto validator
- [ ] Wear OS: no wear module in this project

## Playlists and lyrics

- [x] Manual playlists with ordered membership and drag-and-drop reordering
- [x] Smart playlists compiled to parameterised SQL
- [x] M3U / M3U8 import and export, including EXTINF
- [x] Embedded and sidecar .lrc lyrics with binary-search auto-scroll
- [ ] Online lyrics download: needs a provider choice and privacy policy;
      LyricsRepository.downloadLyrics returns false rather than pretending
- [ ] Floating lyrics overlay (requires SYSTEM_ALERT_WINDOW)
- [ ] Collaborative playlists (requires a backend; out of scope for offline-first)

## UI

- [x] Material 3, Material You dynamic color, AMOLED true-black variant
- [x] Edge-to-edge, adaptive navigation (bottom bar / rail / drawer)
- [x] Tablet and foldable column scaling via window size classes
- [x] Glassmorphism surfaces and GPU-blurred artwork backdrop
- [x] Five visualizer styles on a single hardware-accelerated Canvas
- [x] Gestures: swipe to queue, swipe to favorite, double-tap seek, pinch artwork
- [~] Shared element transitions: navigation is in place, the shared transition
      scope is not wired into list-to-detail routes yet
- [~] Artwork download and manual artwork selection: model fields exist, picker
      and fetcher are not implemented

## Advanced / network

- [ ] Chromecast: CastPlayer seam identified, cast dependency not added
- [ ] DLNA, SMB, FTP, WebDAV, NAS streaming: MediaSourceLocation and SourceType
      model the concept, no DataSource implementations exist
- [ ] USB DAC and bit-perfect output: not reachable through the public ExoPlayer
      API; requires AAudio exclusive mode or a vendor SDK

## Quality

- [x] Hilt DI throughout, Room, Coroutines, Flow, Paging 3, Coil, Media3
- [x] KDoc on public APIs and on every non-obvious decision
- [x] Unit tests for LRC parsing and smart-playlist compilation
- [x] RTL-safe typography, Persian locale resources, content descriptions on controls
- [~] Test coverage is illustrative, not comprehensive: view model and Compose UI
      tests still need to be written
- [~] No deprecated APIs were used knowingly; this cannot be verified without a
      green build

## Known issues to fix on the first CI runs

These are the places where drift between modules is most likely, listed in the
order they will surface:

1. `SmartPlaylistCompiler` and its test reference `SmartOperator` / `SmartField`
   entries (`NOT_CONTAINS`, `STARTS_WITH`, `IN_LAST_DAYS`, `FAVORITE`, `FOLDER`,
   `DATE_ADDED`, `PLAY_COUNT`) that may not all exist in `core/model`.
2. `MusicRepositoryImpl` calls a wide DAO surface (`applyScanBatch`,
   `deleteMissing`, the `paging*` queries, `findDuplicates`, `findMissingArtwork`)
   that must match `SongDao` exactly.
3. `PlaylistRepositoryImpl` uses `PlaylistKind.MANUAL` and `PlaylistSongCrossRef`
   while the model uses `PlaylistKind.USER` and `PlaylistSongEntity`.
4. `BackupRepositoryImpl` calls `SettingsRepository.importJson`, which is not
   implemented yet (only `exportJson` exists).
5. `LyricsRepositoryImpl` uses `MediaMetadataRetriever` with `use {}`, which is
   only AutoCloseable on API 29+ while minSdk is 26.
6. Launcher icons are placeholders: the manifest points at `@drawable/ic_note`.
7. The Gradle wrapper JAR is not committed; CI installs Gradle directly.

## Suggested order of work

1. Get `:app:assembleDebug` green in CI, fixing the drift listed above.
2. Generate real launcher icons and a committed Gradle wrapper.
3. Wire jaudiotagger for real tag writing and ReplayGain tag parsing.
4. Implement crossfade with a second player instance.
5. Add the cast dependency and the network DataSource implementations.
6. Add view model and Compose UI tests.
