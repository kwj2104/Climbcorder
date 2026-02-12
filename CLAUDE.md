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

Single-activity app (`MainActivity`) with manual fragment switching via `supportFragmentManager` (no Navigation Component). Bottom navigation has three tabs:

- **HomeFragment** — Monthly calendar grid built programmatically. Queries Room database for recording dates and shows blue dot indicators under days with recordings. Today is highlighted with a blue circle.
- **CameraFragment** — CameraX-based video recording to MediaStore.
- **Settings** — Placeholder fragment.

## Database

Room database (`AppDatabase`) with a single entity `ClimbRecording` (named to avoid clash with `androidx.camera.video.Recording`). Singleton pattern with seed data inserted via `addCallback` on first creation. DAO queries use epoch millis ranges.

## Key Build Notes

- **KSP** (`2.2.10-2.0.2`) is used for Room annotation processing
- `android.disallowKotlinSourceSets=false` in `gradle.properties` is a required workaround for KSP + AGP 9 built-in Kotlin ([google/ksp#2729](https://github.com/google/ksp/issues/2729))
- Dependencies are managed via `gradle/libs.versions.toml` version catalog
- Compile/Target SDK: 36, Min SDK: 24
