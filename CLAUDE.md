# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

This is an Android project using AGP 9.0.0 with Gradle 9.1. AGP 9 bundles Kotlin natively — do **not** apply the `kotlin-android` plugin separately.

JAVA_HOME must point to a JDK. Android Studio's bundled JBR works:

```bash
# Set JAVA_HOME (required — not in system PATH by default)
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"  # bash/Git Bash
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"  # PowerShell

# Build debug APK
./gradlew :app:assembleDebug

# Run unit tests
./gradlew :app:testDebugUnitTest

# Run instrumented tests
./gradlew :app:connectedDebugAndroidTest
```

On Windows, `./gradlew` works in Git Bash; use `gradlew.bat` in CMD/PowerShell.

## Architecture

Single-activity app (`MainActivity`) with manual fragment switching via `supportFragmentManager` (no Navigation Component). Code is organized into subpackages:

- **`com.example.climbcorder`** — `MainActivity` (root package, referenced by manifest)
- **`com.example.climbcorder.data`** — `ClimbRecording`, `RecordingDao`, `AppDatabase`, `VideoItem`, `VideoRepository`
- **`com.example.climbcorder.ui`** — All fragments and adapters

Bottom navigation has four tabs:

- **HomeFragment** — Monthly calendar with heatmap-colored dots (5-level intensity based on daily recording duration). Shows activity stats (sessions, total time, streak) and an activity feed of recent recordings. Bluetooth connection indicator via broadcast receiver.
- **CameraFragment** — CameraX video recording to MediaStore (`Movies/HelloWorld`). Bluetooth headset media button control via `MediaSessionCompat` (plays silent audio to claim session). Voice announcements on start/stop. 3-second recording cooldown.
- **LibraryFragment** — 4-column grid of recorded videos from MediaStore with Coil thumbnails. Tap to play in `PlayerFragment` (Media3/ExoPlayer with custom minimal controls).
- **SettingsFragment** — "Keep app awake" setting with configurable timeout (30s–10m). Navigates to `AwakePickerFragment` for duration selection.

### Keep-Awake Logic

`MainActivity` sets `FLAG_KEEP_SCREEN_ON` and schedules removal after the configured timeout. Timer resets on every `onUserInteraction()`. Flag is cleared on `onPause()`. Setting stored in SharedPreferences (`"climbcorder_prefs"`, key `"keep_awake_seconds"`, default 30).

### Fragment Navigation

- Main tabs replace fragments in `R.id.container` (no back stack)
- Sub-screens (`PlayerFragment`, `AwakePickerFragment`) use `addToBackStack` / `popBackStack`

## Database

Room database (`AppDatabase`) with a single entity `ClimbRecording` (named to avoid clash with `androidx.camera.video.Recording`). Singleton pattern with seed data inserted via `addCallback` on first creation. DAO queries use epoch millis ranges.

`VideoRepository` wraps MediaStore queries for video metadata (duration, date, thumbnails) — the Room database tracks recording timestamps while actual video files live in MediaStore.

## Key Build Notes

- **KSP** (`2.2.10-2.0.2`) is used for Room annotation processing
- `android.disallowKotlinSourceSets=false` in `gradle.properties` is a required workaround for KSP + AGP 9 built-in Kotlin ([google/ksp#2729](https://github.com/google/ksp/issues/2729))
- Dependencies are managed via `gradle/libs.versions.toml` version catalog (some deps still inline in `build.gradle.kts`)
- Compile/Target SDK: 36, Min SDK: 24

## Key Dependencies

| Library | Purpose |
|---------|---------|
| CameraX 1.3.1 | Camera preview & video recording |
| Media3/ExoPlayer 1.5.1 | Video playback with custom controls |
| Room 2.8.4 | Local database |
| Coil 2.7.0 | Image/video thumbnail loading |
| AndroidX Media 1.7.0 | MediaSessionCompat & MediaButtonReceiver |
| Material 1.11.0 | BottomNavigationView, UI components |
