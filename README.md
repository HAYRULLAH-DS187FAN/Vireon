# Vireon

**Create without limits.**

Vireon is a free, ad-free and watermark-free Android video editor. Media preview and future editing/export operations are designed to run locally on the device.

## Current stage — 0.1.0 foundation

- Kotlin + Jetpack Compose interface
- Android 10 (API 29) and newer
- Local video selection using Android's system media picker
- On-device video playback preview with Media3 ExoPlayer
- Editor shell with a side tool rail and a wide lower timeline area
- Portrait and landscape layouts
- Media3 Transformer and Effect dependencies prepared for later editing/export implementation
- GitHub Actions workflow that builds and uploads a debug APK

The current foundation is not yet a complete video editor. Timeline editing, real editing tools, project persistence and final export will be implemented in later stages.

## Build

Requirements: JDK 17, Android SDK Platform 36 and Gradle 8.13.

If the Gradle wrapper is not present locally, generate it once:

```bash
gradle wrapper --gradle-version 8.13 --distribution-type bin
```

Then build:

```bash
./gradlew assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Push to `main` (or run the workflow manually) to build the debug APK. Download the `vireon-debug-apk` artifact from the workflow run.

## Product principles

- Free to use
- No ads
- No watermark
- On-device media processing
- No account or cloud upload required for editing

## Technology

- Kotlin
- Jetpack Compose
- AndroidX Media3 ExoPlayer
- AndroidX Media3 Transformer
