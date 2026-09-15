# Plan 003 — Weekly digest + "Add expense" home-screen shortcut

## Summary
- **Digest**: opt-in weekly notification summarizing last week's spend, top
  category, and remaining budget. `PeriodicWorkRequest` (7 days, Monday ~09:00
  local) + settings toggle; cancel on disable.
- **Shortcut**: static `shortcuts.xml` with an "Add expense" shortcut
  deep-linking straight into the numpad entry state.

## Digest
- `WeeklyDigestWorker` (`CoroutineWorker`, Hilt `@HiltWorker` if the project's
  existing workers use it — check `RecurrentExpenseNotificationWorker`'s
  construction pattern and match it; it is a plain `CoroutineWorker` per
  round-1 notes, so follow that unless otherwise refactored since).
- Scheduling helper enqueues with initial delay computed to next Monday 09:00
  via `TimeProvider`-derived clock (round-1 pattern — do not call
  `LocalDate.now()` directly in injectable logic; worker boundary is acceptable).
- Data: reuse the aggregates the digest needs from `BudgetStateCalculator` /
  repository flows (last-7-days total, top category by spend, current period
  remaining).
- Settings toggle `weeklyDigestEnabled: Boolean = false` in `UserSettings` +
  DataStore; toggling enqueues/cancels the unique periodic work
  (`UniqueWorkResolver`-style keep policy, name `"weekly_digest"`).
- New notification channel `"digest"` (low importance) in
  `NotificationHelper.createNotificationChannels()`.
- Deep-link tap → main screen (same pendingIntent pattern as existing
  notifications).

## Shortcut
- `app/src/main/res/xml/shortcuts.xml` — one static shortcut:
  `android:shortcutId="add_expense"`, icon, `android:shortcutShortLabel` from
  strings, intent to `MainActivity` with extra/action
  `com.sachit.moneypal.action.ADD_EXPENSE`.
- Register via `meta-data android:name="android.app.shortcuts"` on the
  launcher activity in `AndroidManifest.xml`.
- `MainActivity` (or the nav host's `LaunchedEffect`) handles the action by
  navigating/opening the numpad entry state.
- FOSS flavor check: static shortcuts need no Play Services — safe.

## Strings
- ~6 new strings (digest title/body placeholders, shortcut label) +
  es/fr translations.

## Verification
- `./gradlew :app:testFossDebugUnitTest` — unit-test the digest aggregation
  inputs (pure calculator function for "top category last 7 days" if not
  already trivially covered).
- Manifest lint via `:app:compileFossDebugKotlin` + full unit test gate.
