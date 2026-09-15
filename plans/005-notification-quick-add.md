# Plan 005 — Notification quick-add

## Summary
Log an expense by replying to a notification (period-end, recurrent, and
credit-card channels), without opening the app.

## Implementation notes
- Add a `RemoteInput` direct-reply action ("Log expense") in
  `NotificationHelper` for the period-end, recurrent-expense, and credit
  channels.
- `QuickAddReceiver` — manifest-registered `BroadcastReceiver`,
  `android:exported="false"`, `goAsync()` + a short coroutine (or hand off to
  a `CoroutineWorker`) since Room calls are suspend: reads
  `RemoteInput.getResultsFromIntent(intent)`, parses the text as
  `<amount>[ <category>]` (e.g. `12.50 groceries`; bare number = uncategorized).
  Amount parsing must reuse the numpad's decimal parsing rules
  (BigDecimal, dot and comma both accepted — match existing parser used by
  widgets/BankSmsParser if present).
- Insert via `BudgetRepository.addTransactionIfAbsent` into the **current**
  period (compute periodId the same way `BudgetTransactionHandler` does;
  respect the past-period-end queueing rule — reuse that logic, don't fork it).
- Feedback: update the notification with a confirmation ("Logged 12.50 ·
  groceries") or an error style ("Couldn't read that — try 12.50 groceries").
  Optional undo action reusing the existing undo-notification pattern from
  BankSmsParser capture (if the pattern is extractable cheaply; otherwise a
  simple confirm-and-clear).
- Guard: if app lock (`AppLockController`, plan 009) is enabled, quick-add
  **still works** but marks the entry identically to manual entry — no lock
  bypass concern since no data is displayed in the reply UI. Note this in the
  KDoc.

## Strings
- ~5 new strings (action label, hint, success/error templates) + es/fr.

## Verification
- Unit-test the reply-text parser as a pure function (`QuickAddParser`):
  bare amounts, amount+category, comma decimals, garbage input.
- `./gradlew :app:testFossDebugUnitTest` full gate; compile wear flavor too
  since NotificationHelper is in main source set shared paths.
