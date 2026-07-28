# Architecture

NeonBeat is a multi-module Clean Architecture app. This document explains the
layering, the data flow, and the reasoning behind the decisions that are not
obvious from the code.

## Layering

    feature/*  ->  domain  ->  core/*
                     ^
                     |  (Hilt bindings only)
                   data

- feature modules know only domain interfaces and the design system.
- domain is pure Kotlin: interfaces, models re-exported from core/model, use cases.
- data implements the domain interfaces and is wired in exactly one place,
  data/di/DataModule.kt. Swapping an implementation for a fake in tests is a
  one-module change.
- core modules are leaf libraries: model, common, database, datastore,
  designsystem, media.

Why multi-module: incremental builds stay fast (a change in feature/settings
does not recompile the scanner), and the dependency direction is enforced by
Gradle rather than by convention.

## Data flow

1. MediaStoreScanner reads the system media index in batches of 500 rows.
2. Rows are upserted into Room inside a single transaction; rows whose URIs
   disappeared are deleted in the same pass.
3. Room is the single source of truth. Every screen reads from Room, never from
   MediaStore directly.
4. Repositories expose Flow and PagingData; view models add screen state.
5. Playback commands go to the MediaLibraryService; playback state comes back
   through PlaybackConnection.

Why Room instead of querying MediaStore live: MediaStore cannot express
favorites, play counts, smart-playlist rules, or FTS ranking, and a cursor query
per scroll event is far too slow at 100k rows.

## Playback

core/media owns everything audio:

- MusicService is a MediaLibraryService. It survives the UI, so playback keeps
  running when the activity is destroyed.
- NeonPlayerFactory builds the ExoPlayer with audio-focus handling,
  becoming-noisy handling, wake mode and the extension renderers.
- MediaLibrarySessionCallback implements the browse tree, which is what Android
  Auto and Wear clients navigate.
- AudioEffectsController attaches Equalizer, BassBoost, Virtualizer and
  LoudnessEnhancer to the current audio session, and detaches them when the
  session goes away.
- PlaybackStateWriter persists the queue and records qualifying plays.

Single-source-of-truth rule: the session is authoritative. The UI never keeps a
separate copy of isPlaying or position, which is why the notification, the
widget, the QS tile, Bluetooth and the app can never disagree.

## Threading

- Dispatchers are injected via qualifiers (@IoDispatcher, @DefaultDispatcher,
  @MainDispatcher) so tests can substitute a test dispatcher.
- Repositories apply flowOn(io) at their boundary; view models never switch
  dispatchers themselves.
- Nothing does disk or network work on the main thread; StrictMode is enabled in
  debug builds to catch regressions.

## Database

- 10 entities, version 1, WAL journal mode enabled in a Room callback.
- songs_fts is an FTS4 virtual table used only for search ranking.
- Aggregates (albums, artists, genres, folders) are SQL projections over songs
  rather than duplicated tables, so a rescan can never leave them stale.
- Paging queries are indexed on the exact sort column used by the UI.

## Settings

Preferences DataStore holds roughly 50 fields in a single UserSettings model.
Reads are a Flow, writes are suspending and atomic. There is no save button:
every toggle writes through, so the UI always shows persisted state.

## Testing strategy

- Pure logic (LRC parsing, smart-playlist compilation, queue building) is tested
  on the JVM with plain JUnit, no Robolectric needed.
- Repositories are testable by injecting fake DAOs, because DAOs are interfaces.
- Compose screens are tested with createAndroidComposeRule plus a Hilt test
  runner that replaces DataModule with fakes.

## Known constraints

- The project was never compiled in the authoring environment; the GitHub
  Actions workflow is the first real build. Treat the first green run as part of
  setup.
- Android 11+ scoped storage means all tag edits, renames, moves and deletes go
  through MediaStore write requests, which require user confirmation.
- Bit-perfect output and USB DAC control are not achievable through the public
  ExoPlayer API alone; see ROADMAP.md.
