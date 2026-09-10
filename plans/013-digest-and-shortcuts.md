# Plan 013 — Weekly digest notification + launcher quick-add shortcuts

Written against commit **`614cb49`** (`git rev-parse --short HEAD` should print
`614cb49`). If HEAD differs, re-verify excerpts before executing.

## Why this matters

Two cheap, high-perceived-value additions reuse infrastructure that already
works:

1. **Weekly digest** — the notification stack (`AlarmManager` exact alarms +
   `WorkManager` recurrent workers via `NotificationScheduler`) is exact and
   battle-tested (plans 002/003 hardened it), but it only fires reactive
   reminders (period end, recurring dues). A proactive "you've spent X of Y
   this week" digest is the feature users actually *look forward to*.
2. **Quick-add shortcuts** — a long-press app shortcut ("Add expense") skips
   2 taps per entry. The repo already has a Quick Settings tile
   (`NewTransactionTileService.kt`) proving demand for fast entry, and there is
   **no `shortcuts.xml` / `ShortcutManager` usage anywhere** (verified).

## Current state (verified excerpts)

`presentation/notification/NotificationScheduler.kt` (fully read):
- Exact-alarm helper pattern with `canScheduleExactAlarms()` fallback
  (`scheduleMidnightPeriodCheck`, `schedulePeriodEndNotification`).
- WorkManager pattern with unique-name REPLACE policy
  (`scheduleRecurrentExpenseNotification` → `enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, ...)`).
- `initializeNotifications()` is called at app start and re-schedules
  everything — a weekly digest work can enqueue itself here.

`presentation/notification/NotificationHelper.kt` builds the actual
notifications (reuse its channel/style helpers; read it before touching
channels).

`app/src/main/AndroidManifest.xml` — `MainActivity` is the launcher activity
(single-activity Compose app, `navigation/Screen.kt` defines routes). No
`android.app.shortcuts` meta-data exists.

`NewTransactionTileService.kt` — the tile launches the app into the editor
state; find the deep-link/intent extra it uses (read the file; the digest plan
must reuse the identical entry mechanism so behavior is consistent).

`UserSettings` has `notificationHour/Minute` (period end) and
`recurrentNotificationHour/Minute` — the digest adds its own time pair
following the identical pattern.

## Design decisions

- **Digest via WorkManager `PeriodicWorkRequest`, 7 days**, not AlarmManager:
  exactness doesn't matter for a summary, WorkManager survives reboots and
  battery optimization, and the repo already enqueues unique-work by name.
  Flexible window: 7 days ± 1h.
- **Opt-in, default off** (matches `smsCaptureEnabled` precedent; proactive
  notifications without consent get apps uninstalled).
- **Content**: spent this week vs. weekly allocation
  (`BudgetState.totalSpentThisWeek` vs `weeklyAllocation` — both already
  computed in `BudgetStateCalculator`), plus count of transactions. Computed
  in the worker at fire time from the repository, never cached.
- **Shortcuts**: two **static** shortcuts (manifest XML — dynamic shortcuts
  are unnecessary complexity for v1): "Add expense" (opens editor with the
  same intent the tile uses) and "View history" (opens main screen history
  tab if a route/intent extra exists — check `Screen.kt`; if the app has no
  deep-linkable history route, ship only the Add-expense shortcut).

## In scope

- `app/src/main/java/com/sachit/moneypal/domain/model/UserSettingsModel.kt`
- `app/src/main/java/com/sachit/moneypal/data/repository/SettingsRepository.kt` + `SettingsRepositoryImpl.kt`
- `app/src/main/java/com/sachit/moneypal/presentation/notification/NotificationScheduler.kt`
- New: `app/src/main/java/com/sachit/moneypal/presentation/notification/WeeklyDigestWorker.kt`
- `app/src/main/java/com/sachit/moneypal/presentation/notification/NotificationHelper.kt`
- `app/src/main/java/com/sachit/moneypal/presentation/ui/settings/Settings.kt` + `SettingsViewModel.kt` (digest toggle + time picker, modeled on the existing period-end notification time row)
- New: `app/src/main/res/xml/shortcuts.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- Tests: `app/src/test/java/com/sachit/moneypal/presentation/notification/WeeklyDigestCalculatorTest.kt` (pure math), extend `SettingsViewModelTest.kt`

## Out of scope

- Monthly digests, digest tap-through analytics charts (tap opens app main
  screen only), rich notification styles beyond BigText.
- Dynamic/push shortcuts, shortcut pinning APIs.

## Steps

1. **Settings.** Add to `UserSettings`:
   `weeklyDigestEnabled: Boolean = false`, `digestNotificationHour: Int = 10`,
   `digestNotificationMinute: Int = 0` (10:00 default — outside both existing
   notification defaults). Add
   `setWeeklyDigestEnabled(Boolean)` / `setDigestNotificationTime(Int, Int)` to
   `SettingsRepository` following the exact per-field DataStore pattern used by
   `setRecurrentNotificationTime`. Extend `SettingsRepositoryImplTest` round-trip.

2. **DigestCalculator** (pure object in the worker file or separate):
   inputs `List<Transaction>`, `weekStart: LocalDate`, `weeklyAllocation:
   BigDecimal`, `currency formatting deferred to render` → output data class
   `WeeklyDigest(spentThisWeek: BigDecimal, allocation: BigDecimal, txCount: Int,
   isOverWeekly: Boolean)`. Week = trailing 7 days ending *yesterday*
   (`weekStart = today.minusDays(6)` … `today.minusDays(1)`), including only
   `!isDeleted && !isRecurrent && !isAdjustment && amount > 0`. **This is the
   only digest math; unit-test it table-style** (weekday boundary cases
   included).

3. **WeeklyDigestWorker** (`CoroutineWorker`, not `@HiltWorker` — matching the
   existing `RecurrentExpenseNotificationWorker` pattern from plan 003's
   deviation note: clock reads at the `doWork` boundary). `doWork`: read
   settings; if disabled → `Result.success()` (and the scheduler won't have
   enqueued it anyway); fetch transactions via `BudgetRepository`
   (Hilt `EntryPointAccessibly` style — copy however
   `RecurrentExpenseNotificationWorker` obtains its deps; read that file
   first). Compute digest, format amounts with the existing
   `WidgetCurrencyFormatter` (it already centralizes currency+symbol
   rendering for widgets — reuse rather than duplicating), post via
   `NotificationHelper` on the same channel policy (read the helper; create a
   dedicated channel `weekly_digest` only if the helper's channel factory
   supports it, else reuse the reminders channel). Always
   `Result.success()`; a missed digest is better than a retry loop.

4. **Scheduling.** In `NotificationScheduler` add:

   ```kotlin
   fun scheduleWeeklyDigest(hour: Int, minute: Int) {
       val initialDelay = durationUntilNext(LocalDayOfWeek.MONDAY, hour, minute) // helper in this class
       workManager.enqueueUniqueWork(...)  // NO — Periodic:
       workManager.enqueueUniquePeriodicWork(
           "weekly_digest",
           ExistingPeriodicWorkPolicy.UPDATE,
           PeriodicWorkRequestBuilder<WeeklyDigestWorker>(7, TimeUnit.DAYS)
               .setInitialDelay(initialDelay)
               .build()
       )
   }
   ```

   Compute `initialDelay` to the next Monday at (hour, minute) local time —
   write a small `internal fun nextMondayAt(hour, minute, now: LocalDateTime):
   LocalDateTime` and **unit-test it**. Call `scheduleWeeklyDigest` from
   `initializeNotifications()` (guarded by the setting) and from both new
   settings setters' UI callbacks. When the toggle turns off, cancel via
   `workManager.cancelUniqueWork("weekly_digest")`.

5. **Manifest + shortcuts.xml.** `res/xml/shortcuts.xml`:

   ```xml
   <shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
       <shortcut
           android:shortcutId="add_expense"
           android:enabled="true"
           android:icon="@drawable/ic_shortcut_add"   <!-- or an existing adaptive icon; reuse the tile's icon -->
           android:shortcutShortLabel="@string/shortcut_add_expense">
           <intent android:action="com.sachit.moneypal.action.ADD_EXPENSE"
               android:targetPackage="com.sachit.moneypal"
               android:targetClass="com.sachit.moneypal.presentation.MainActivity" />
       </shortcut>
   </shortcuts>
   ```

   In `MainActivity`, handle the `ADD_EXPENSE` action exactly the way
   `NewTransactionTileService` triggers editor entry (read that service and
   mirror its mechanism — likely an intent extra consumed in
   `MainActivity`/`MainScreenViewModel`). Add to the manifest inside
   `<activity android:name=".presentation.MainActivity">`:
   `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />`
   plus an `<intent-filter>` for the custom action. If the tile launches via
   an activity alias or different mechanism, follow *that* instead — the
   shortcut must behave identically to the tile.

6. **Settings UI.** Settings notification section: digest toggle row +
   time picker row (copy the structure of the period-end notification time
   row in `Settings.kt`; strings below). Toggling on asks WorkManager to
   schedule; changing time re-schedules (`ExistingPeriodicWorkPolicy.UPDATE`
   makes this cheap — do not CANCEL+REPLACE, which resets the period).

7. **Strings** (`values/strings.xml`): `settings_weekly_digest`
   ("Weekly summary"), `settings_weekly_digest_summary` ("Get a spending
   summary every Monday"), `settings_digest_time` ("Summary time"),
   `digest_notification_title` ("Your week in spending"),
   `digest_notification_over` ("You've spent %1$s of your %2$s weekly budget across %3$d purchases."),
   `digest_notification_under` ("You've spent %1$s of your %2$s weekly budget across %3$d purchases — nicely on track."),
   `shortcut_add_expense` ("Add expense").

8. **Tests.** `WeeklyDigestCalculatorTest` (pure): inclusion/exclusion rules,
   trailing-7-day window, over/under flag; `nextMondayAtTest`: now=Sunday
   23:59 → next day; now=Monday 09:00 with target 09:00 → today? No — must
   return *next* Monday (never schedule in the past / immediate fire); DST is
   out of scope (local time zone math via `LocalDateTime` is acceptable).
   Extend `SettingsViewModelTest`: toggling digest calls the right repo
   setter and scheduler function (fake the scheduler interface — if
   `NotificationScheduler` is a concrete class, wrap or use the existing
   MockK patterns in that test file).

## Verification gates

```bash
./gradlew :app:compileFossDebugKotlin
./gradlew :app:testFossDebugUnitTest
```

Manual on device: enable digest → `adb shell dumpsys jobscheduler | grep -i weekly` (or WorkManager inspect) shows periodic job; toggle shortcut long-press on launcher icon shows "Add expense"; tapping it lands directly in the editor with the numpad focused.

## Done criteria

- [ ] Digest fires weekly at the configured time with correct trailing-7-day numbers (verify by comparing against History totals).
- [ ] Toggle off cancels the periodic work.
- [ ] Long-press shortcut adds an expense in ≤2 taps.
- [ ] Verification gates green.

## Maintenance notes

- `nextMondayAt` and the digest calculator are the only pure logic — keep them
  that way; the worker stays a thin shell (plan 003's clock-injection rule:
  clock reads at the worker boundary).
- If a future plan adds notification channels management UI, fold
  `weekly_digest` channel (if created) into it.

## Escape hatches — STOP and report if

- `RecurrentExpenseNotificationWorker` obtains dependencies via a mechanism
  other than expected (it is NOT `@HiltWorker` per plan 003's deviation note) —
  mirror whatever it actually does; do not introduce a second DI pattern.
- The launcher icon/tile entry mechanism cannot be mirrored cleanly
  (`NewTransactionTileService` uses an API unavailable to static shortcuts) —
  ship a plain `ACTION_VIEW`-style fallback that opens MainActivity and report
  the deviation.
- Manifest already contains a `shortcuts` meta-data (none found in recon —
  if it now exists, reconcile instead of duplicating).
