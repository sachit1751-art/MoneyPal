# Plan 045 — Notification actions: mark paid / snooze recurring reminders

**Status:** TODO
**Written against commit:** `48bb35b` (2026-09-23)
**Category:** feature (S–M)
**Depends on:** plan 029 (worker error contract, landed)
**Effort:** S–M · **Risk:** low

## Why

Recurring payment notifications currently only *inform*. For due payments the
single most common response — "paid it" or "not this week" — requires opening
the app, finding the entry, and marking the occurrence. Two action buttons on
the notification close that loop in one tap. (This revives the dropped round-5
plan 040 in a reduced, worker-safe form.)

## Current state (verified)

- Recurring reminders are scheduled via AlarmManager + WorkManager
  (architecture per AGENTS.md; plan 029 fixed bounded retries and completion
  results).
- `PaidRecurrentOccurrenceEntity` records settled occurrences;
  `PaidRecurrentOccurrence.paidAt == SKIPPED_OCCURRENCE_MARKER` (-1L) marks
  **skipped** (`TransactionModel.kt`, `containsOccurrence` matches on id+date
  only).
- The SMS-undo notification (plan 023) already demonstrates action-button
  wiring with a correct row id round-trip.
- Notification permission flow exists (`refreshNotificationPermission` in
  `SettingsViewModel`).

## Steps

1. **Receiver**: `NotificationActionReceiver` (Hilt `BroadcastReceiver`) with
   two actions: `ACTION_MARK_OCCURRENCE_PAID` and
   `ACTION_SNOOZE_OCCURRENCE`; extras carry `transactionId` +
   `occurrenceDateEpochDay` (epochDay, not formatted strings).
2. **Mark paid path**: receiver delegates to an injected use case that inserts
   a `PaidRecurrentOccurrence` (real `paidAt` = now) — the same path the
   in-app "mark paid" button uses; **no duplicated logic**.
3. **Snooze path**: snoozing re-schedules the reminder via the existing
   AlarmManager scheduler (+ default 1 day, configurable later); it must
   **not** touch occurrence state.
4. **Builder**: attach
   `NotificationCompat.Action` ×2 (icons + strings ×3 locales) in the
   existing recurring-reminder builder; unique
   `requestCode`s; set `setAutoCancel(true)` on mark-paid.
5. **Cancellation**: after mark-paid, update/cancel the notification by id
   (reuse the tag/id scheme the reminder builder uses).
6. **Settings**: a toggle "Quick actions on payment reminders"
   (`notification_quick_actions`, default on) in the notification settings
   block; DataStore key + repo setter + `UserSettings` field + repo tests.

## Out of scope

- Snooze-duration picker, actions on SMS-capture notifications, wear,
  Android 12+ exact-alarm policy changes.

## Test plan

- Receiver/use-case test (JVM, Robolectric-free where possible): mark-paid
  inserts the occurrence with correct id/date; snooze calls the scheduler and
  does not insert; unknown transactionId → no-op (fail silent, matches plan
  029's error contract).
- Repo round-trip test for the new toggle.

## Done criteria

1. `grep -rn "ACTION_MARK_OCCURRENCE_PAID\|ACTION_SNOOZE_OCCURRENCE"
   app/src/main --include="*.kt"` — receiver + builder references.
2. Verification gate exit 0.

## Escape hatches

- If the reminder builder doesn't currently carry the transactionId, report
  and wire the id through the alarm intent first (small prep commit) instead
  of guessing ids from notification tags.
