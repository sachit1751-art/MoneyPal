# Plan 041 — Privacy hardening: auto-lock timeout + screenshot blocking

**Status:** TODO
**Written against commit:** `467ce9d` (2026-09-19) — round 5 (features)
**Category:** feature (privacy, small)
**Depends on:** round-4 plans 023–029 should land first (they own the current
`AppLockController` area); technically independent
**Effort:** S · **Risk of fix:** low

## Why

MoneyPal has a biometric app lock (plan 009) but it is all-or-nothing in two
ways:

1. The lock re-engages on **every** background→foreground transition. Users who
   check a balance quickly find this annoying and disable it entirely — which
   is worse for privacy than a short timeout would be.
2. The app's content is visible in the **recents screen and screenshots** even
   when the lock is enabled. Competing finance apps set `FLAG_SECURE`.

Both are small, self-contained changes that complete an existing feature.

## Conventions (every round-5 plan)

- User-facing strings in `app/src/main/res/values/strings.xml` **plus
  `values-es` and `values-fr` copies** (other locales are Crowdin-managed; do
  not hand-edit them).
- Money math is `BigDecimal`/plain strings, never `Double` (not exercised here).
- Pure logic goes in `domain/` with JUnit4 + Truth tests (pattern:
  `app/src/test/java/com/sachit/moneypal/domain/calculator/NoSpendStreakCalculatorTest.kt`).
- Conventional commits, one commit for this plan.
- Global verification gate:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

## Current state (verified)

- `app/src/main/java/com/sachit/moneypal/presentation/lock/AppLockController.kt`:
  holds `_isLocked` state; `refreshLock()` is called from
  `MainActivity.onStart` (MainActivity.kt ~line 163) and re-locks whenever the
  activity stops/starts. There is **no timeout concept** anywhere in the class.
- `MainActivity.kt` ~line 314 renders `AppLockOverlay` when
  `appLockController.isLocked` is true. No `FLAG_SECURE` call exists anywhere
  in the repo (`grep -rn FLAG_SECURE app/src` → 0 matches).
- Settings repository already stores a boolean:
  `SettingsRepositoryImpl.kt` line 50: `const val APP_LOCK_ENABLED_KEY_NAME =
  "app_lock_enabled"`; `SettingsRepository.kt` line 60:
  `setAppLockEnabled(enabled: Boolean)`; `UserSettingsModel.kt` line 37:
  `appLockEnabled`.
- Settings UI toggle lives in `Settings.kt` ~lines 481-484
  (`settings_app_lock_title`) wired via `SettingsViewModel.onAppLockToggle()`
  (SettingsViewModel.kt ~line 288 guards on `canAuthenticate()`).

## Steps

### Step 1 — Domain: auto-lock policy

New `app/src/main/java/com/sachit/moneypal/domain/lock/AutoLockPolicy.kt`:

```kotlin
enum class AutoLockTimeout(val minutes: Int) {
    IMMEDIATELY(0), ONE_MINUTE(1), FIVE_MINUTES(5);
}

object AutoLockPolicy {
    /** True when the lock should be re-engaged now. */
    fun shouldLock(
        enabled: Boolean,
        timeout: AutoLockTimeout,
        lastPausedAtEpochMs: Long?,
        nowEpochMs: Long,
    ): Boolean {
        if (!enabled) return false
        if (timeout == AutoLockTimeout.IMMEDIATELY) return true
        val pausedAt = lastPausedAtEpochMs ?: return true
        return nowEpochMs - pausedAt >= timeout.minutes * 60_000L
    }
}
```

### Step 2 — Settings plumbing

- `UserSettingsModel`: add `autoLockTimeout: AutoLockTimeout =
  AutoLockTimeout.IMMEDIATELY` (import from domain; the enum is small and
  serializable by name).
- `SettingsRepository` + `SettingsRepositoryImpl`: add
  `setAutoLockTimeout(timeout: AutoLockTimeout)` and map it through DataStore
  with key name `"auto_lock_timeout"` stored as its `name` string (pattern:
  `THEME_MODE` at SettingsRepositoryImpl.kt line ~190, which stores an enum by
  name). Unknown stored values fall back to `IMMEDIATELY`.
- Backup compatibility: do **not** add the field to `BackupModels.kt` in this
  plan (backup schema stays byte-identical; the setting is device-local like
  other lock settings — verify `appLockEnabled` itself is not in
  `BackupModels.kt` before assuming otherwise; if it IS included, mirror it the
  same way with a safe default on restore).

### Step 3 — Controller wiring

`AppLockController`:

- Inject/observe the timeout setting alongside `appLockEnabled`.
- Record `lastPausedAtEpochMs` when the host activity stops (the controller
  already has lifecycle hooks driven from `MainActivity`; add a
  `onHostPaused()`/`onHostResumed()` pair or set the timestamp in `refreshLock()`
  callers — keep the controller Android-free so it stays unit-testable).
- On resume: `locked = AutoLockPolicy.shouldLock(...)` instead of
  unconditionally locking.

### Step 4 — FLAG_SECURE

In `MainActivity.onCreate`, after `super.onCreate`:

```kotlin
if (settingsRepository-driven appLockEnabled) {
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE)
}
```

Read the setting once at creation; toggling the setting applies on next launch
(document that in the settings subtitle string). Do NOT clear the flag when the
lock is disabled mid-session — out of scope.

### Step 5 — Settings UI

In `Settings.kt` under the existing app-lock toggle: a second row
("Auto-lock after") opening a radio dialog with the three timeout options, and
update the app-lock subtitle to mention screenshot blocking
(`settings_app_lock_subtitle`, strings.xml line 171). All three locales.

## Out of scope

- Changing `AppLockOverlay` visuals or the biometric prompt flow.
- Per-screen locking, fake-cover overlays, intrusion detection.
- Wear module (excluded by maintainer request).

## Test plan

- New `app/src/test/java/com/sachit/moneypal/domain/lock/AutoLockPolicyTest.kt`
  (JUnit4 + Truth, pattern: `NoSpendStreakCalculatorTest`): disabled → never
  locks; immediately → always; 1-minute boundary at exactly 60_000ms locks,
  59_999ms does not; null pausedAt locks.
- Extend `SettingsRepositoryImplTest.kt` (exists, uses in-memory DataStore) with
  set/get round-trip for the timeout + unknown-value fallback.
- Manual: enable lock + 1-minute timeout, background the app, resume
  immediately → unlocked; wait >1 min → locked.

## Done criteria (machine-checkable)

1. `grep -rn "AutoLockPolicy" app/src/main --include="*.kt"` — controller and
   MainActivity references exist.
2. `grep -rn "FLAG_SECURE" app/src/main --include="*.kt"` — exactly one usage
   site in `MainActivity`.
3. `./gradlew :app:testFossDebugUnitTest` — exit 0, including the new tests.
4. Compile gate (above) — exit 0.

## Maintenance notes

- If a later plan adds widgets showing balances, FLAG_SECURE does not protect
  widget content (system-rendered) — note this in review if widgets gain
  sensitive detail.
- `AutoLockTimeout` is persisted by name; never rename the enum constants
  without a migration.

## Escape hatches

- If `AppLockController` turns out to be tied to Activity lifecycle in a way
  that makes the pause-timestamp untestable without instrumentation, STOP and
  report the coupling instead of dragging Compose/state deps into `domain/`.
- If `appLockEnabled` IS serialized in backups and restore writes it, report —
  the timeout field must then follow the same path (with defaults), not the
  device-local path assumed above.
