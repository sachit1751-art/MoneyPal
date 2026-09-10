# Plan 009 — Biometric app lock

Written against commit **`614cb49`** (`git rev-parse --short HEAD` should print
`614cb49`). If HEAD differs, re-verify excerpts before executing.

## Why this matters

MoneyPal stores a full spending history, income adjustments, and bank-SMS
captures. There is **no app-lock code anywhere in the repo** (verified: no
`BiometricPrompt`/`fingerprint` references outside `plans/`). For a finance app,
a fingerprint/PIN gate is the single most trust-building feature and is
consistently among the most-requested Android features. AndroidX provides
`androidx.biometric:biometric` for exactly this; it is not currently a
dependency.

## Current state (verified excerpts)

`app/src/main/java/com/sachit/moneypal/domain/model/UserSettingsModel.kt` —
settings are a flat data class with a matching `SettingsRepository` setter per
field. Existing boolean-toggle pattern (there are many, e.g.
`isAmoledEnabled`, `smsCaptureEnabled`):

```kotlin
val isAmoledEnabled: Boolean = false,
```

and in `SettingsRepository.kt`:

```kotlin
suspend fun setAmoledEnabled(enabled: Boolean)
```

`SettingsRepositoryImpl.kt` persists each with a DataStore boolean key (see
`setSmsCaptureEnabled` for the exact template at ~line 460+ of that file).

`presentation/MainActivity.kt` is the single Activity (manifest-verified entry
point) hosting Compose navigation via `AppNavGraph.kt` (screens: main, history
tab, analytics, settings, editor, onboarding, bug report, changelog history —
see `navigation/Screen.kt`).

## Design decisions

- **androidx.biometric** (`BiometricPrompt` with `DEVICE_CREDENTIAL | BIOMETRIC_WEAK`):
  one library covers fingerprint + face + PIN/pattern/password, with automatic
  fallback to the device credential. Do NOT hand-roll a PIN screen.
- **Gate at the Activity, not per-screen.** A single `LaunchedEffect`-driven
  lock overlay in `MainActivity` covers every screen (including settings, where
  the toggle itself lives) with one code path.
- **Unlock is memory-only.** `locked` is a `MutableState` in the Activity. If
  the process dies, the lock re-engages — that is the desired behavior. Do not
  persist an "unlocked" flag anywhere.
- **Default off**, matching every other privacy-adjacent toggle in this app
  (`smsCaptureEnabled` defaults false).

## In scope

- `app/build.gradle.kts` (add `implementation(libs.androidx.biometric)` + catalog entry in `gradle/libs.versions.toml`)
- `app/src/main/java/com/sachit/moneypal/domain/model/UserSettingsModel.kt`
- `app/src/main/java/com/sachit/moneypal/data/repository/SettingsRepository.kt` + `SettingsRepositoryImpl.kt`
- `app/src/main/java/com/sachit/moneypal/presentation/MainActivity.kt`
- New: `app/src/main/java/com/sachit/moneypal/presentation/lock/AppLockController.kt`
- `app/src/main/java/com/sachit/moneypal/presentation/ui/settings/Settings.kt` + `SettingsViewModel.kt` (new toggle row, modeled on the AMOLED row)
- `app/src/main/res/values/strings.xml`
- Tests: `app/src/test/java/com/sachit/moneypal/presentation/ui/settings/SettingsViewModelTest.kt` (extend)

## Out of scope

- Per-screen or auto-lock-on-background timers (v2 idea; record in plan only).
- Wear app lock.
- Hiding notification contents when locked.

## Steps

1. **Dependency.** In `gradle/libs.versions.toml` add `biometric = "1.1.0"` to
   `[versions]`, `androidx-biometric = { group = "androidx.biometric", name =
   "biometric", version.ref = "biometric" }` to `[libraries]`. In
   `app/build.gradle.kts` dependencies block add
   `implementation(libs.androidx.biometric)`.

2. **Settings plumbing.** Add `val appLockEnabled: Boolean = false` to
   `UserSettings` (next to `isAmoledEnabled`). In `SettingsRepository` add
   `suspend fun setAppLockEnabled(enabled: Boolean)`. In
   `SettingsRepositoryImpl`, copy the `setAmoledEnabled` implementation
   verbatim, changing only the DataStore key name (`app_lock_enabled`) and
   mapper. Add the field to both directions of the existing settings-mapping
   (the file maps `UserSettings` ↔ DataStore keys in `observeSettings`).

3. **AppLockController** (`presentation/lock/AppLockController.kt`):

   ```kotlin
   @Singleton
   class AppLockController @Inject constructor(
       @param:ApplicationContext private val context: Context,
       private val settingsRepository: SettingsRepository,
   ) {
       /** True while the UI must be obscured. */
       val isLocked = MutableStateFlow(false)

       suspend fun initialize() {
           isLocked.value = settingsRepository.getSettings().appLockEnabled
       }

       /** Returns null when no lock is needed; else a configured prompt. */
       suspend fun buildPromptInfo(): BiometricPrompt.PromptInfo? {
           if (!settingsRepository.getSettings().appLockEnabled) return null
           return BiometricPrompt.PromptInfo.Builder()
               .setTitle(context.getString(R.string.app_lock_prompt_title))
               .setAllowedAuthenticators(
                   BiometricManager.Authenticators.BIOMETRIC_WEAK or
                       BiometricManager.Authenticators.DEVICE_CREDENTIAL
               )
               .build()
       }

       fun unlock() { isLocked.value = false }
   }
   ```

   Note the repo's Hilt convention: `@param:ApplicationContext` (see
   `NotificationScheduler.kt`), not bare `@ApplicationContext`.

4. **MainActivity gate.** In `MainActivity.onCreate` after `setContent`, in
   composition: collect `appLockController.isLocked`; when true, draw a
   full-screen overlay **on top of the NavHost** (Box wrapper): a surface with
   `R.string.app_lock_overlay_title`, a lock icon, and a "Unlock" button that
   shows the `BiometricPrompt` (activity-scoped `BiometricPrompt(activity,
   executor, callback)`; on `authenticationSucceeded` call `unlock()`; on
   error show a snackbar with the error string). Initialize with
   `LaunchedEffect(Unit) { appLockController.initialize() }`. When the toggle
   is switched ON in Settings, immediately challenge with a prompt to verify
   the device actually has a credential — if `BiometricManager.canAuthenticate`
   returns `BIOMETRIC_ERROR_NONE_ENROLLED`, show a snackbar pointing at system
   security settings and revert the toggle to false (do this in
   `SettingsViewModel`, which already handles toggle side effects).

5. **Settings row.** In `Settings.kt`, duplicate the AMOLED toggle row inside
   the same section, string names: `settings_app_lock` ("App lock"),
   `settings_app_lock_summary` ("Require fingerprint or screen lock to open
   MoneyPal"), prompt strings `app_lock_prompt_title` ("Unlock MoneyPal"),
   `app_lock_overlay_title` ("MoneyPal is locked").

6. **Test.** Extend `SettingsViewModelTest.kt`: toggling `onAppLockToggled(true)`
   with a faked `AppLockController` reporting "no credential enrolled" reverts
   the setting to false and emits a snackbar effect; with "credential ok" the
   setting persists. Follow the existing fake-repository pattern in that file.

## Verification gates

```bash
./gradlew :app:compileFossDebugKotlin
./gradlew :app:testFossDebugUnitTest
```

Manual (no emulator automation available for biometrics): on a device, enable
the toggle → prompt appears; kill & relaunch app → overlay blocks UI until
authenticated; disable toggle → app opens freely.

## Done criteria

- [ ] With the toggle on, cold-start shows the lock overlay before any budget data renders.
- [ ] Toggle cannot be left on when no device credential exists.
- [ ] `testFossDebugUnitTest` green including the new tests.

## Maintenance notes

- `AppLockController` is the seam for future auto-lock-on-background
  (`Activity.onStop` → `isLocked.value = true`) — deliberately not implemented
  now because it annoys users until refined.
- Any new screen added to `AppNavGraph` is automatically covered; do not add
  per-screen locks.

## Escape hatches — STOP and report if

- `MainActivity` hosts more than one Activity in the manifest (multi-activity
  navigation would need per-Activity gating).
- The settings mapping in `SettingsRepositoryImpl` turns out to be generated or
  table-driven rather than per-field (then follow that table's pattern instead).
- androidx.biometric 1.1.0 conflicts at compile time with the Compose BOM
  versions in `libs.versions.toml` — do not silently upgrade other libraries;
  report.
