# AGENTS.md

Guidance for AI agents and future maintainers working on this repository.

## Project

**MoneyPal** is an Android budget-tracking app built with Kotlin and Jetpack
Compose. It records daily spending with a calculator-style numpad, tracks
budgets/periods, recurring expenses and subscriptions, credit-card due dates,
CSV import/export, home-screen widgets, and a Wear OS companion app.

- Owner / maintainer: **Mr Sachit** (`sachit1751-art`, sachit1751@gmail.com)
- Application ID: `com.sachit.moneypal`
- Base package: `com.sachit.moneypal` (app), `com.sachit.moneypal.wear` (Wear module),
  `com.sachit.moneypal.sync.contract` (shared sync protocol module)

> History note: this project was forked/rebranded from the original "Minus"
> app. Avoid reintroducing the old name, package (`com.serranoie.app.minus`),
> or original-author attribution anywhere.

## Modules

| Module | Path | What it is |
|:-------|:-----|:-----------|
| `:app` | `app/` | Phone app. Flavors: `foss` (F-Droid/Izzy-safe, no Play Services) and `wear` (includes Wear OS bridge). Source sets: `main`, `foss`, `wear`, `test`, `androidTest`. |
| `:wear` | `wear/` | Standalone Wear OS app (companion to `:app`). |
| `:sync-contract` | `sync-contract/` | Pure-Kotlin module sharing the DataStore/sync serialization contract (`WearSyncProtocol`) between phone and watch. |

## Architecture

- **MVI** (per-screen `*MviContract`, intents, `ViewModel` + `UiState`).
- **Clean-ish layers**: `presentation/ui/*`, `domain/` (models, use cases, calculators),
  `data/` (Room entities/DAOs, repositories, DataStore).
- **DI**: Hilt/Dagger. **Persistence**: Room (schemas exported to `app/schemas/`)
  + DataStore preferences. **Background**: AlarmManager + WorkManager.
- **Compose** UI (Material 3), custom theme system in `presentation/ui/theme/`.

## Build prerequisites

- JDK 17 (the Gradle wrapper is 8.13; it does **not** run on newer JDKs such as 25 —
  use a JDK 17/21 toolchain).
- Android SDK with platform 36, `ANDROID_HOME` set or `local.properties`
  (`sdk.dir`) present. `local.properties` is gitignored.
- `gradle.properties` must **not** contain machine-specific paths (e.g.
  toolchain install locations) — keep it portable.

## Common commands

Flavor-qualified tasks are required — `:app` has no default flavor, so bare
tasks like `:app:compileDebugKotlin` or `:app:testDebugUnitTest` are ambiguous
and fail.

```bash
# Compile everything
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :wear:compileDebugKotlin :sync-contract:compileKotlin

# Compile test sources
./gradlew :app:compileFossDebugUnitTestKotlin :sync-contract:compileTestKotlin

# Unit tests (foss flavor; includes Paparazzi screenshot tests under app/src/test)
./gradlew :app:testFossDebugUnitTest

# Instrumented E2E tests (needs a device/emulator) + Paparazzi snapshot verification
./gradlew :app:connectedFossDebugAndroidTest :app:verifyPaparazziFossDebug --continue
```

Notes:

- **Room schema renames**: exported schemas live in `app/schemas/<package>.data.local.AppDatabase/`.
  If the database class package ever changes, move the `*.json` files to the new
  folder too, or Room auto-migration generation fails with
  `Schema 'N.json' required for migration was not found`.
- `preBuild` runs `:app:generateChangelogKotlin`, which regenerates
  `app/build/generated/source/changelog/GeneratedChangelog.kt` from
  `fastlane/metadata/android/*/changelogs/*.txt`. Commit `.txt` changelogs for
  releases; the generated Kotlin is deterministic and not committed.

## Conventions

- **User-facing text** goes in string resources (`values`, `values-es`,
  `values-fr`, plus Crowdin-managed locales). Do not hardcode strings in code.
- **Package/id naming**: `com.sachit.moneypal` everywhere — never the old
  `com.serranoie.app.minus`.
- Log tags historically used the owner's name as a prefix (e.g. `ISAAC:...`);
  these were renamed to `SACHIT:...`. Keep tags consistent and generic.
- **Commits**: conventional commits style (see `CONTRIBUTING.md`).
- Keep date/budget/recurrence logic out of UI when reusable — it's unit-tested
  in `domain/`.

## Releases (fastlane)

- Version lives in `version.properties` (`VERSION_NAME`, `VERSION_CODE`).
- `bundle exec fastlane android prep_release version_tag:v1.2.3` generates the
  changelog `.txt` and updates version files — commit **before** tagging.
- `publish_github` builds and publishes APKs; `deploy_play_store` uploads the
  AAB. Play credentials are local/gitignored (`fastlane/*.json`), so a new
  owner must supply their own before running Play lanes.

## CI

`.github/workflows/`: `pr-check.yml` (build + checks), `release.yml`
(tagged releases), `play-store.yml` (Play upload). Play store metadata and
listing text live under `fastlane/metadata/`.
