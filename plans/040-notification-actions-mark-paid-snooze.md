# Plan 040 — Notification actions: mark paid / snooze from the notification

**Status:** TODO
**Written against commit:** `467ce9d` (2026-09-19) — round 5 (features)
**Category:** feature (small, friction-killer)
**Depends on:** plan 029 (worker error contract) should land first — this plan
extends the same worker/receiver layer; otherwise technically independent
**Effort:** S–M · **Risk of fix:** low-medium (background writes)

## Why

When a recurrent-expense or credit-card-due notification fires, the user must
open the app, find the expense, and mark it paid. The notification itself
offers no actions. Android action buttons let the user settle a subscription
payment ("Mark paid") or defer the reminder ("Snooze 1 day") in two taps from
the shade.

The repo already has all the building blocks: `QuickAddReceiver` proves the
pattern of a `BroadcastReceiver` performing a DB write from a notification
action, `UndoSmsCaptureReceiver` (plan 023) proves the transaction-id-extra
pattern, and `MarkRecurrentOccurrencePaidIntegrationTest` proves the
mark-paid use case works from any caller.

## Conventions (every round-5 plan)

- User-facing strings in `app/src/main/res/values/strings.xml` **plus
  `values-es` and `values-fr` copies** (other locales are Crowdin-managed).
- Money = `BigDecimal`/plain strings, never `Double`.
- Pure logic in `domain/` with JUnit4 + Truth tests.
- Conventional commits, one commit for this plan.
- Global verification gate:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

## Current state (verified)

- `app/src/main/java/com/sachit/moneypal/presentation/notification/QuickAddReceiver.kt`
  — a `BroadcastReceiver` that writes a transaction from a notification
  action; it obtains collaborators via Hilt `EntryPoint` (read it and mirror
  its DI pattern exactly; do not invent a new one).
- `app/src/main/java/com/sachit/moneypal/presentation/notification/QuickAddExpenseWriter.kt`
  — shows the canonical background-write path (defaults
  `paymentMethod = PaymentMethod.OTHER` at line 51).
- `NotificationHelper.kt` (same package) builds the recurrent/credit
  notifications (channels defined at lines ~48, ~112). The recurrent
  notification is posted by `RecurrentExpenseNotificationWorker`
  (WorkManager; see PROJECT_OVERVIEW.md "WorkManager" section).
- `MarkRecurrentOccurrencePaidIntegrationTest`
  (`app/src/androidTest/.../budget/`) exercises the mark-paid flow against a
  real Room DB.
- Mark-paid business logic: find the existing use case under
  `app/src/main/java/com/sachit/moneypal/domain/usecase/` whose name matches
  "mark paid" / occurrence payment (search
  `grep -rn "MarkRecurrent\|markOccurrencePaid" app/src/main --include="*.kt"`)
  and reuse it — do NOT reimplement the recurrence math.

## Steps

### Step 1 — Snooze policy in domain

New `app/src/main/java/com/sachit/moneypal/domain/usecase/SnoozePolicy.kt`
(pure, unit-testable): given a scheduled occurrence time and a snooze choice
(`1_DAY`, `3_DAYS`), compute the new notify-at epoch millis. Keep it a tiny
pure object — no Android imports.

### Step 2 — Mark-paid action receiver

New `app/src/main/java/com/sachit/moneypal/presentation/notification/MarkRecurrentPaidReceiver.kt`:

- `onReceive`: extract the transaction id extra (same extra name convention as
  `UndoSmsCaptureReceiver` uses after plan 023), guard `id <= 0 → return`.
- Resolve the mark-paid use case via the same Hilt `EntryPoint` pattern as
  `QuickAddReceiver`, run it inside `goAsync()` + a coroutine (mirror
  QuickAddReceiver's structure), cancel the notification
  (`NotificationManagerCompat.cancel(id)`).
- On failure: log via `logcat` and record through `ErrorLogRecorder` (exists;
  see `CsvImportWorker` usage pattern) — never crash from a receiver.

### Step 3 — Snooze action receiver

New `SnoozeRecurrentNotificationReceiver.kt` in the same package:

- Computes the snoozed time via `SnoozePolicy`, then re-enqueues the existing
  recurrent-notification WorkManager request with that delay (find how
  `RecurrentExpenseNotificationWorker` is enqueued today — search
  `enqueue` in `presentation/notification/` — and reuse that request builder;
  the input data must carry the same transaction id).
- Cancels the current notification. Snoozing twice is fine (idempotent
  re-enqueue with a unique-enough work name per transaction id + occurrence).

### Step 4 — Wire the actions into the notifications

In `NotificationHelper` (or the worker, wherever the `NotificationCompat.Builder`
for recurrent + credit-card-due notifications is constructed): add
`addAction(...)` with `PendingIntent.getBroadcast` (FLAG_IMMUTABLE +
FLAG_UPDATE_CURRENT) for "Mark paid" and "Snooze 1 day". Strings:
`notification_action_mark_paid`, `notification_action_snooze_1d` (+ es/fr).
Only add "Mark paid" when the payload actually refers to a recurrent
transaction id (the worker knows; pass a boolean through the notification
builder call).

## Out of scope

- Direct-reply / RemoteInput actions.
- Budget period-end notification actions.
- Any change to recurrence math or the mark-paid semantics.
- Wear module.

## Test plan

- `SnoozePolicyTest.kt` in `app/src/test/java/com/sachit/moneypal/domain/usecase/`
  (JUnit4 + Truth): 1-day and 3-day offsets are exact epoch-math; DST-spanning
  offsets stay 24h/72h in absolute millis (document that choice).
- Instrumented (optional if a device is unavailable, note skipped):
  extend the existing mark-paid integration test to invoke
  `MarkRecurrentPaidReceiver` via a crafted intent and assert the occurrence
  is marked.
- Unit-test the receivers' guard logic only if their intent-parsing is
  extracted into a testable function; do not force-instantiate receivers in
  Robolectric-free tests.

## Done criteria (machine-checkable)

1. `grep -rn "notification_action_mark_paid" app/src/main/res/values/strings.xml
   app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml`
   — 1 match in each of the three files.
2. `./gradlew :app:compileFossDebugKotlin` — exit 0.
3. `./gradlew :app:testFossDebugUnitTest` — exit 0 (SnoozePolicyTest green).
4. Manual on device/emulator: create a recurrent expense due tomorrow, force
   the worker (`adb shell am broadcast` or temporary `Now` enqueue in debug),
   tap "Mark paid" → notification clears and occurrence shows paid; tap
   "Snooze" → a new notification fires after the chosen delay.

## Maintenance notes

- The extra-name and work-name conventions here will be reused by future
  notification actions — keep them in one constants object in the
  notification package.
- Plan 029's completion-result contract applies to any new worker code; do not
  regress it.

## Escape hatches

- If the mark-paid use case requires an open `periodId` that a background
  receiver cannot determine, STOP and report — do not guess period resolution
  in a receiver.
- If `QuickAddReceiver`'s DI pattern cannot be replicated (e.g. it depends on
  an Activity-scoped entry point), report the blocker instead of switching to
  a global-scope hack.
