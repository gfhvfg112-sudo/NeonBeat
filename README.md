# NeonBeat

A premium, offline-first Android music player built with Kotlin, Jetpack Compose and Material 3, architected for libraries of 100,000+ tracks.

> Read this first. This repository is a complete, modular Android Studio project: build scripts, dependency catalog, database, media service, repositories, design system, feature screens and tests. It has NOT been compiled locally, because the environment it was authored in has no Android SDK, no Gradle daemon and no network access. The GitHub Actions workflow in `.github/workflows/android.yml` is the first real build. Expect to fix import-level and API-signature issues on the first runs. See ROADMAP.md for exactly what is implemented, what is scaffolded, and what is intentionally stubbed.

## Getting started

Requirements:

| Tool | Version |
| --- | --- |
| Android Studio | Ladybug (2024.2) or newer |
| JDK | 17 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.1.0 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 (Android 8.0) |

Build:

    gradle :app:assembleDebug

Run tests:

    gradle testDebugUnitTest

The Gradle wrapper JAR is intentionally not committed; CI installs Gradle 8.11.1 directly. Locally, run `gradle wrapper` once to generate it.

## Continuous integration

`.github/workflows/android.yml` runs on every push to `main`:

- `build` assembles the debug APK and uploads it as the `neonbeat-debug-apk` artifact.
- `test` runs JVM unit tests and uploads the HTML reports.
- On failure, all build reports are uploaded as `build-reports` for diagnosis.

## Module map

    app                  Application, MainActivity, navigation, widget, QS tile
    core/model           Pure Kotlin domain models (no Android deps)
    core/common          Dispatchers, qualifiers, shared utilities
    core/database        Room entities, DAOs, FTS4 index, aggregates
    core/datastore       Preferences DataStore + UserSettings
    core/designsystem    Theme, typography, components, visualizer
    core/media           Media3 service, session, effects, playback control
    domain               Repository interfaces + use cases
    data                 Repository impls, MediaStore scanner, tags, M3U, lyrics
    feature/library      Songs / albums / artists / genres / folders
    feature/player       Now playing, queue, gestures, lyrics
    feature/search       Instant FTS search
    feature/settings     Preferences

Dependency rule: feature -> domain -> core. The data module is bound to domain
interfaces only through Hilt (data/di/DataModule.kt), so no feature ever imports
a concrete repository.

## Architecture

Clean Architecture + MVVM + repository pattern.

- UI: Compose only, one state holder per screen, state exposed as StateFlow, events as Channel.
- Domain: plain Kotlin interfaces and use cases, no Android imports, unit-testable on the JVM.
- Data: Room is the single source of truth. MediaStore is an input, not a live query surface.
- Playback: a MediaLibraryService owns the player. The UI observes it through PlaybackConnection,
  so notification, Bluetooth, Android Auto and widget controls all stay in sync.

Full detail in ARCHITECTURE.md.

## Performance approach

The 100k-song target drives most design decisions:

| Concern | Approach |
| --- | --- |
| Scanning | Batched MediaStore cursor reads (500 rows), cached column indices, bulk upserts in one transaction |
| Listing | Paging 3 straight from Room PagingSource; page 60, prefetch 120, max 600 in memory |
| Scrolling | Stable keys and contentType on every lazy item so recomposition stays scoped |
| Search | FTS4 virtual table, 120 ms debounce, flatMapLatest cancellation, per-section result caps |
| Artwork | Coil with heap-percentage memory cache, bounded disk cache, hardware bitmaps |
| Startup | Splash held only until the theme preference resolves; scan runs in WorkManager, not onCreate |
| Battery | Effects and visualizer capture attach only while their surfaces are visible |

## Feature coverage

Implemented here: MediaStore scanning with .nomedia handling, folder/album/artist/genre
browsing, favorites, recently added, play statistics, gapless playback, audio effects
(equalizer, bass boost, virtualizer, loudness enhancer), sleep timer with fade-out,
playback speed and pitch, balance and mono, MediaSession with Android Auto browse tree,
queue persistence, smart playlists, M3U import/export, LRC and embedded lyrics, instant
search, Material You and AMOLED theming, glassmorphism, gesture-driven now-playing screen,
five GPU visualizer styles, tag editing, batch operations, backup and restore, home-screen
widget and Quick Settings tile.

Stubbed or scaffolded, documented rather than silently missing: Chromecast, DLNA/SMB/FTP/
WebDAV network sources, Wear OS module, online lyrics and artwork download, bit-perfect USB
DAC output. Each has a defined seam and is listed in ROADMAP.md.

## Permissions

| Permission | Why |
| --- | --- |
| READ_MEDIA_AUDIO (API 33+) / READ_EXTERNAL_STORAGE | Read local audio files |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK | Background playback |
| POST_NOTIFICATIONS | Playback notification (API 33+) |
| WAKE_LOCK | Keep playback alive with the screen off |
| MODIFY_AUDIO_SETTINGS | Equalizer and audio effects |

Writing to files the app does not own goes through MediaStore.createWriteRequest, so the
user always confirms tag edits and deletions.

## License

No license file is included. Add one before distributing.
