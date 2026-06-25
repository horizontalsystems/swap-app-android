# CLAUDE.md

Guidance for working in this repository.

## Project

Swap App — a native Android app (`io.horizontalsystems.swapapp`) built with Kotlin and Jetpack Compose. Currently a scaffold from the Android Studio Compose template; UI is a single `MainActivity` rendering a Compose `Scaffold`.

## Tech stack

- **Language:** Kotlin `2.2.10`
- **UI:** Jetpack Compose (Material 3), Compose BOM `2026.02.01`
- **Build:** Gradle `9.4.1` (wrapper) with Android Gradle Plugin `9.2.1`, Kotlin DSL build scripts
- **JDK:** 17 (project compiles Java/Kotlin to `JavaVersion.VERSION_11` bytecode)
- **SDK:** `compileSdk 37`, `targetSdk 37`, `minSdk 28`
- Dependency versions are centralized in `gradle/libs.versions.toml` (version catalog). Reference them via `libs.*` in build files rather than hardcoding versions.

## Common commands

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run JVM unit tests (app/src/test)
./gradlew connectedDebugTest   # run instrumented tests (needs a device/emulator)
./gradlew lint                 # run Android lint
./gradlew installDebug         # install on a connected device/emulator
./gradlew clean                # clean build outputs
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Layout

- `app/src/main/java/io/horizontalsystems/swapapp/` — app code
  - `MainActivity.kt` — entry point / Compose host
  - `ui/theme/` — Compose theme (`Theme.kt`, `Color.kt`, `Type.kt`)
- `app/src/main/res/` — Android resources (strings, icons, themes)
- `app/src/main/AndroidManifest.xml` — manifest
- `app/src/test/` — JVM unit tests; `app/src/androidTest/` — instrumented tests
- `app/build.gradle.kts` — module build config; `gradle/libs.versions.toml` — version catalog

## Notes

- `local.properties` holds `sdk.dir` and is local-only (not version controlled).
- This project requires SDK platform `android-37` installed (the AndroidX dependencies require compiling against API 37).