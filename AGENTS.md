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

- JDK 21 for the Gradle daemon (pinned via `gradle/gradle-daemon-jvm.properties`);
  the wrapper is Gradle 9.5.0 with AGP 9.3.3. The launcher JVM may be newer
  (e.g. Android Studio's JBR 25) — only the daemon must be 21.
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

# One-click: build all distributable release APKs into dist/<version>-<sha>/
scripts/build-apks.sh              # foss + phone-wear + watch app
scripts/build-apks.sh --with-tests # gate on the unit suite first

# Compile test sources
./gradlew :app:compileFossDebugUnitTestKotlin :sync-contract:compileTestKotlin

# Unit tests (foss flavor; includes Paparazzi screenshot tests under app/src/test)
./gradlew :app:testFossDebugUnitTest

# Instrumented E2E tests (needs a device/emulator) + Paparazzi snapshot verification
./gradlew :app:connectedFossDebugAndroidTest :app:verifyPaparazziFossDebug --continue
```Notes:

- **Room schema changes** — the database is at **version 25** (24: `wallet_balance`;
  25: `transactions.smsSender` for the plan-048 mute actions). Bumping the
  version requires ALL of: entity field (`@ColumnInfo(defaultValue = ...)`) →
  domain model + repo mappers → `autoMigrations` entry in `AppDatabase` →
  exported `app/schemas/.../<N>.json` → a `Migration` object when the
  auto-migration needs back-fill (e.g. `MIGRATION_24_25`) **registered in
  `DatabaseModule.addMigrations`** (a shipped-but-unregistered migration broke
  the build once) → migration test in `AppDatabaseMigrationTest`.
  If the database class package ever changes, move the `*.json` files to the
  new folder too, or Room fails with `Schema 'N.json' required for migration
  was not found`.
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
- **SMS capture**: bank SMS parsing is a single generic parser
  (`domain/sms/BankSmsParser`, generic-patterns design decided 2026-09-08 —
  do NOT switch to per-bank templates). Merchant extraction, confidence
  scoring and dedupe live in `domain/sms/`; ingestion goes through
  `ProcessIncomingSmsUseCase` → `SmsIngestWorker`. Per-sender muting
  (`mutedSmsSenders` in DataStore) gates parsing; the sender is persisted on
  the transaction (`smsSender` column) so review-inbox rows and the capture
  notification can offer "Mute sender".
- **Feature planning docs**: `plans/` (rounds 4–6) was removed 2026-09-26 after
  every plan landed and its round-6 review gaps closed. Historical specs live
  in git history; review reports in `docs/reviews/`. New planning docs may
  recreate the directory as needed.

## Releases (fastlane + local)

- Version lives in `version.properties` (`VERSION_NAME`, `VERSION_CODE`);
  versionCode = major*10000 + minor*100 + patch (2.3.0 → 20300).
- Changelog `.txt` for the new versionCode must be committed in
  `fastlane/metadata/android/en-US/changelogs/` (+ `es-ES`) **before** the tag
  is pushed — the `publish_github` lane hard-fails without it (deliberate:
  CI never regenerates changelogs).
- Tagging `vX.Y.Z` triggers `release.yml`, which needs 4 GitHub secrets
  (`MONEYPAL_RELEASE_KEYSTORE_BASE64`, `..._STORE_PASSWORD`, `..._KEY_ALIAS`,
  `..._KEY_PASSWORD`). **These are currently unset** — every tagged release
  since v2.2.0 failed at the secret-validation step; v2.1.0 was the last
  CI-signed release.
- Local signed releases work instead: put `keystore.properties` (gitignored)
  in the project root and run `scripts/build-apks.sh`. Both `:app` **and**
  `:wear` read it (the watch module gained signing in 2026-09-26; before that
  its release APK was silently unsigned).
- **Signing key history**: the original keystore is unavailable. v2.3.0 is
  signed with a NEW keystore created 2026-09-26 (RSA-4096, alias `moneypal`).
  Users on ≤ v2.1.0 must uninstall before installing (signature mismatch).
  A backup of the keystore + `keystore.properties` is kept off-repo in the
  maintainer's `Documents/MoneyPal-release-keystore-backup/`; the password is
  in the maintainer's password manager. Losing both = no more updatable
  releases on this signature.

## CI

`.github/workflows/`: `pr-check.yml` (build + checks), `release.yml`
(tagged releases — currently failing on missing signing secrets, see
Releases), `play-store.yml` (Play upload — also needs credentials).
Play store metadata and listing text live under `fastlane/metadata/`.
Until the secrets are restored, cut releases locally with
`scripts/build-apks.sh` and attach the APKs to the GitHub Release manually.
