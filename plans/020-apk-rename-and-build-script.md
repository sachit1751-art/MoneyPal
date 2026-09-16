# Plan 020 — APK rename + one-click build script wiring

**Status:** TODO
**Written against commit:** `ff6f773` (2026-09-16)
**Category:** DX (audit findings #7, #8)
**Depends on:** nothing (land before the next release train)
**Effort:** S · **Risk:** low (one output-name string + release-lane glob)

## Why

1. **Name collision:** `app/build.gradle.kts` (wear *flavor*) and
   `wear/build.gradle.kts` (standalone watch *module*) both emit
   `MoneyPal-WearOS-v<version>.apk`. The release lane globs
   `app/build/outputs/apk/{wear,foss}/release/*.apk` so CI is unaffected, but
   any local "build everything" flow copies one APK over the other.
2. **No one-click build:** releases require the fastlane lane (changelog
   committed, `gh` auth, signing secrets). A maintainer wants
   "run script → get all APKs from latest code, no mistakes".

The script `scripts/build-apks.sh` is ALREADY committed (JDK 17–21 detection,
SDK check, unsigned-with-warning fallback, optional `--with-tests` gate,
dist/ output with unambiguous names). This plan wires the repo around it.

## Steps

### Step 1 — Rename the app wear-flavor APK output

In `app/build.gradle.kts`, the `applicationVariants.all { outputs.all { ... } }`
block sets `MoneyPal-WearOS-v$appVersionName.apk` for the wear flavor. Change
to:

```kotlin
output.outputFileName = if (flavorName.contains("wear", ignoreCase = true)) {
    "MoneyPal-phone-wear-v$appVersionName.apk"
} else {
    "MoneyPal-v$appVersionName.apk"
}
```

### Step 2 — Rename the standalone watch-module APK output

In `wear/build.gradle.kts`:

```kotlin
output.outputFileName = "MoneyPal-watchapp-v$appVersionName.apk"
```

### Step 3 — Extend the release-lane glob defensively

In `fastlane/Fastfile` (`publish_github` lane, `apk_paths = Dir[...]`), the
glob still works (the two app flavors live in `wear,foss` dirs). Add the
watch app explicitly so it ships too — ONLY if the maintainer wants the watch
APK on GitHub Releases (ask; default is yes since the watch app is published):

```ruby
apk_paths = Dir[
  File.expand_path("../app/build/outputs/apk/{wear,foss}/release/*.apk", __dir__),
  File.expand_path("../wear/build/outputs/apk/release/*.apk", __dir__)
].select { |path| File.exist?(path) }
```

### Step 4 — Document + make the script runnable

- `chmod +x scripts/build-apks.sh` (commit the executable bit).
- Add to `BUILD_LOCALLY.md` (file exists at repo root) a top section:
  "One-click: `scripts/build-apks.sh` (add `--with-tests` to gate on the unit
  suite). Outputs land in `dist/<version>-<sha>/` with unambiguous names."
- Add `.gitignore` entry `dist/` if absent.

### Step 5 — Script smoke check (manual, no CI)

Run `scripts/build-apks.sh` once on the maintainer machine; verify `dist/`
contains exactly three APKs (foss, phone-wear, watchapp) and none overwrote
another (size/timestamps differ). This is the plan's real acceptance test —
the script is bash and cannot be unit-tested here.

## Out of scope

- CI workflow changes (release.yml works via fastlane; PR-check untouched).
- AAB bundle naming (`deploy_play_store` lane uploads a single bundle).
- Signing automation beyond the existing keystore.properties fallback.

## Test plan

- No unit tests (build plumbing). The compile gate + the Step 5 smoke run are
  the verification.

## Done criteria

1. `./gradlew :app:assembleFossRelease :app:assembleWearRelease :wear:assembleRelease` — exit 0.
2. `ls app/build/outputs/apk/wear/release/` shows `MoneyPal-phone-wear-*.apk`;
   `ls wear/build/outputs/apk/release/` shows `MoneyPal-watchapp-*.apk`.
3. `git ls-files -s scripts/build-apks.sh` — mode includes `100` (executable).
4. `grep -n "dist/" .gitignore` — 1 match.

## Maintenance notes

- The fastlane changelog guard means the script CANNOT replace releases —
  it is for local builds and testing. Keep that distinction in docs.
- If a fourth artifact type appears (e.g. RBCW apks), extend
  `scripts/build-apks.sh` `copy_apk` calls — the pattern is one line each.
