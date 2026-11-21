# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Where this fits

This directory is the **mobile app** of the Validat3B monorepo (git root is the parent directory; the root `README.md` calls it `movil/`). Validat3B validates in-store price captures (3 photos: storefront, product, price tag + barcode + metadata) for the Tiendas 3B hackathon and decides `aprobada` / `rechazada` / `revision_manual`. The app runs cheap on-device checks and sends only what it can't resolve confidently to the server in `../servidor/` (AI models). Docs, code comments and UI copy are in Spanish.

Sibling directories that matter to app work:
- `../contratos/` — the **mobile ↔ server contract (v0.1.0)**: `POST /v1/capturas`, `multipart/form-data` with a `captura` JSON part + `foto_fachada` / `foto_producto` / `foto_etiqueta` files; on-device checks go in `chequeos_locales`. Read `../contratos/README.md` before writing networking code. The `*.schema.json` / `openapi.json` files are generated from `servidor/app/contrato.py` — never edit them by hand. `?modo=simulado` returns instantly without AI, and a `capture_id` ending in `-APR` / `-REC` / `-REV` forces the decision — use this for app integration.
- `PLAN.md` (untracked, in this directory) — the app's implementation roadmap: package layout, `core/model` contracts, on-device analysis pipeline (Laplacian blur, brightness, ML Kit barcode/OCR, price parser), and the "Glass Rojo" design system (section 4: all color/type/spacing tokens — don't invent others). It predates some decisions; where it conflicts with reality, reality wins:
  - Its request JSON (section 3: `datos` part, `fachada`/`producto`/`etiqueta` parts, `codigo_barras`/`texto_leido` blocks) is **superseded** by `../contratos/`.
  - It says package `com.equipo.validat3b` and `minSdk 26`; the actual project is `com.richi_mc.hackathon3b` and `minSdk 29`.

Project-wide rules from the root README: 3B data (images, metadata, blind set) never gets committed or sent to other services — keep it under a `datos/` folder (gitignored); secrets go in `.env`. Decision cost asymmetry drives thresholds: approving an invalid capture costs 10, rejecting a valid one 3, manual review 1 — when in doubt, `revision_manual`.

## Current state

Scaffold in place (Fase 0 of PLAN.md): Glass Rojo theme in `ui/theme/GlassRojo.kt` + `ui/components/`, contracts in `core/model`, manual DI in `app/AppContainer.kt` (fakes in `app/Fakes.kt`), routes in `app/NavGraph.kt`. Features live in `feature/{session,camera,analysis,capture}`, persistence/network in `core/{data,network}`. UI is 100% Jetpack Compose — no XML layouts, no Fragments.

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

Camera/OCR/blur features must be tested on a physical phone; the emulator camera is useless for them.

## Build setup — non-obvious bits

The toolchain is very recent; don't "fix" it toward older conventions:

- **AGP 9.x with built-in Kotlin**: there is no `org.jetbrains.kotlin.android` plugin — only `com.android.application` + `org.jetbrains.kotlin.plugin.compose`. Don't add the kotlin-android plugin.
- **R8 config**: release minification is set via the `optimization { enable = true; packageScope = ... }` block (not `isMinifyEnabled`/`proguardFiles`). Keep rules go in `app/src/main/keepRules/*.keep` — there is no `proguard-rules.pro`.
- `compileSdk { version = release(37) }`, `targetSdk = 37`, `minSdk = 29`; Java/Kotlin target 11.
- Gradle 9.6 wrapper; daemon JVM is provisioned as Java 25 via foojay (`gradle/gradle-daemon-jvm.properties`). Configuration cache is enabled in `gradle.properties`.
- All dependency versions live in the version catalog `gradle/libs.versions.toml`; Compose library versions come from the Compose BOM — add new Compose artifacts without a version.
- Repositories are declared only in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`), so don't add `repositories {}` blocks in module build files.

## Orquestación
Delega cada tarea de código de features al agente `worker-android` (Sonnet, `.claude/agents/worker-android.md`); el orquestador es dueño de `core/model`, `app/` (AppContainer, NavGraph, fakes), `ui/` y Gradle, y no implementa features él mismo.
