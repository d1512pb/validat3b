# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Single-module Android app (`:app`, package `com.richi_mc.hackathon3b`) freshly generated from the Android Studio "Empty Compose Activity" template for a hackathon. There is no app logic yet: `MainActivity` hosts a `Greeting` composable inside `Hackathon3BTheme` (`ui/theme/`). UI is 100% Jetpack Compose + Material 3 — no XML layouts, no Fragments.

## Commands

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # build + install on connected device/emulator
./gradlew assembleRelease               # release build (R8 optimization enabled)
./gradlew lint                          # Android lint
./gradlew test                          # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest          # instrumented tests (app/src/androidTest), needs a device

# single test
./gradlew :app:testDebugUnitTest --tests "com.richi_mc.hackathon3b.ExampleUnitTest.addition_isCorrect"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.richi_mc.hackathon3b.ExampleInstrumentedTest
```

## Build setup — non-obvious bits

The toolchain is very recent; don't "fix" it toward older conventions:

- **AGP 9.x with built-in Kotlin**: there is no `org.jetbrains.kotlin.android` plugin — only `com.android.application` + `org.jetbrains.kotlin.plugin.compose`. Don't add the kotlin-android plugin.
- **R8 config**: release minification is set via the `optimization { enable = true; packageScope = ... }` block (not `isMinifyEnabled`/`proguardFiles`). Keep rules go in `app/src/main/keepRules/*.keep` — there is no `proguard-rules.pro`.
- `compileSdk { version = release(37) }`, `targetSdk = 37`, `minSdk = 29`; Java/Kotlin target 11.
- Gradle 9.6 wrapper; daemon JVM is provisioned as Java 25 via foojay (`gradle/gradle-daemon-jvm.properties`). Configuration cache is enabled in `gradle.properties`.
- All dependency versions live in the version catalog `gradle/libs.versions.toml`; Compose library versions come from the Compose BOM — add new Compose artifacts without a version.
- Repositories are declared only in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`), so don't add `repositories {}` blocks in module build files.
