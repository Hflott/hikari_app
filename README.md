# Anime TV (AniList + Placeholder Player)

This is a minimal Android TV app that:
- pulls metadata from AniList (GraphQL)
- lists episodes via a placeholder `EpisodeProvider`
- plays a public test HLS stream via Media3 (ExoPlayer)

## Current Gradle / Android settings
- Kotlin: 2.2.21
- Android Gradle Plugin (AGP): 8.13.2
- Gradle: 8.13 (recommended)
- compileSdk / targetSdk: 36
- minSdk: 23

## Important note about the Gradle Wrapper
The project includes `gradlew` and `gradle-wrapper.properties`, but **does not include**
`gradle/wrapper/gradle-wrapper.jar`.

You can still build and run using Android Studio. If you want to build from the command line,
regenerate the wrapper JAR once on your machine (instructions below).

## Requirements
- Android Studio (recommended)
- Android SDK Platform **36** installed (SDK Manager → SDK Platforms)
- JDK 17 (Android Studio’s Embedded JDK is fine)
- For a physical device: USB debugging or Wireless debugging enabled on the TV

## Build & run in Android Studio (recommended)
1. Extract the zip.
2. Android Studio → **File → Open** → select the `anime-tv-app` folder.
3. Let Gradle sync finish.
4. Install required SDKs if prompted (especially **Android API 36**).
5. Run:
   - Use the device selector (top toolbar) and pick:
     - an Android TV emulator, or
     - your Android TV device (USB / Wi‑Fi)
   - Press **Run**.

## Create an Android TV emulator (optional)
Android Studio → **Tools → Device Manager → Create Device → TV** → choose e.g. “Android TV (1080p)”.
If you hit Windows hypervisor / virtualization errors, using a physical Android TV device is usually faster.

## Install ADB (if `adb` isn’t recognized)
ADB is part of the Android SDK “Platform-Tools”.
- Android Studio → **Tools → SDK Manager → SDK Tools** → check **Android SDK Platform-Tools**.
- On Windows, `adb.exe` is typically here:
  `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`

You can either:
- add that folder to PATH, or
- call ADB by full path.

## Regenerate the Gradle wrapper JAR (optional, for command-line builds)
If you want `./gradlew` to work in a terminal, generate the wrapper once using any local Gradle installation:

- Windows (PowerShell):
  `gradle wrapper --gradle-version 8.13`
- macOS/Linux:
  `gradle wrapper --gradle-version 8.13`

After that you can run:
- `./gradlew assembleDebug`
- `./gradlew installDebug`

## What you should see
- Home → Trending list (from AniList)
- Details → placeholder episodes
- Play → ExoPlayer plays a test HLS stream
