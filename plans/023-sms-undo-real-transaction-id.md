# Plan 023 — SMS undo: return the real inserted row id

**Status:** TODO
**Written against commit:** `664cdc1` (2026-09-17)
**Category:** fix (user-visible bug)
**Depends on:** nothing
**Effort:** S · **Risk of fix:** low

## Why

The "Undo" action on the SMS-capture notification is a **silent no-op**: it can
never delete anything, because the transaction id it receives is always `0`.

Chain of evidence (all verified at commit `664cdc1`):

1. `ProcessIncomingSmsUseCase.invoke` builds the transaction with
   `Transaction.create(...)`, whose data-class contract forces `id = 0`
   (`TransactionModel.kt`, companion `create`: `return Transaction(id = 0, ...)`).
2. `BudgetRepositoryImpl.addTransaction` ignores the `Long` row id that Room's
   `@Insert suspend fun insert(transaction: TransactionEntity): Long` returns:
   ```kotlin
   override suspend fun addTransaction(transaction: Transaction) {
       transactionDao.insert(transaction.toEntity())
   }
   ```
3. The use case therefore reports `Result.Captured(transactionId = transaction.id, ...)`
   — always `0` — and `SmsIngestWorker` passes that into
   `notificationHelper.showSmsCaptureNotification(transactionId = result.transactionId, ...)`,
   which embeds it as the undo intent extra.
4. `UndoSmsCaptureReceiver.onReceive` guards `if (transactionId <= 0L) return` —
   so every undo click cancels the notification and does nothing else. The
   captured expense stays.

## Current state (verified excerpts)

`app/src/main/java/com/sachit/moneypal/data/repository/BudgetRepository.kt` (interface):
```kotlin
suspend fun addTransaction(transaction: Transaction)
```

`app/src/main/java/com/sachit/moneypal/data/repository/BudgetRepositoryImpl.kt` (lines ~304-306):
```kotlin
override suspend fun addTransaction(transaction: Transaction) {
    transactionDao.insert(transaction.toEntity())
}
```

`app/src/main/java/com/sachit/moneypal/domain/usecase/ProcessIncomingSmsUseCase.kt` (lines ~133-146):
```kotlin
if (isPastPeriodEnd) {
    budgetRepository.addQueuedTransaction(transaction)
    logcat(TAG) { "Queued SMS ${match.sender} ${match.amount} for next period" }
} else {
    budgetRepository.addTransaction(transaction)
    logcat(TAG) { "Captured SMS ${match.sender} ${match.amount}" }
}
...
Result.Captured(
    transactionId = transaction.id,
    ...
)
```

Other `addTransaction` callers (verified, all ignore any return value today):
`AddTransactionUseCase` (single call), `MinusCsvService.importTransactions`
(loop insert), `HistoryViewModel` (clone insert).

## Steps

### Step 1 — Make `addTransaction` return the inserted id

Interface (`BudgetRepository.kt`):
```kotlin
/** @return the Room row id of the inserted transaction. */
suspend fun addTransaction(transaction: Transaction): Long
```

Impl:
```kotlin
override suspend fun addTransaction(transaction: Transaction): Long =
    transactionDao.insert(transaction.toEntity())
```

`@Insert` without a conflict strategy returns the new rowId; the entity PK is
`autoGenerate = true`, so this is the transactions-row id. Room does NOT mutate
the passed entity — which is why the id must be returned, not read back from
`transaction.id`.

### Step 2 — Use it in ProcessIncomingSmsUseCase

```kotlin
if (isPastPeriodEnd) {
    budgetRepository.addQueuedTransaction(transaction)
    logcat(TAG) { "Queued SMS ${match.sender} ${match.amount} for next period" }
} else {
    capturedId = budgetRepository.addTransaction(transaction)
    logcat(TAG) { "Captured SMS ${match.sender} ${match.amount}" }
}
...
Result.Captured(
    transactionId = capturedId,
    ...
)
```

Declare `var capturedId = 0L` before the `try` (next to the other locals), so
the queued path keeps `transactionId = 0L` semantics: queued rows have no
transactions-table id yet. `UndoSmsCaptureReceiver` already ignores ids ≤ 0,
and queued entries land in the next period's list where the user can delete
them manually. Do NOT invent a queued-delete flow here. Keep the rest of the
method byte-identical — do NOT reorganize it.

### Step 3 — Update every other implementor/caller the interface change touches

The interface change is a compile error, so let the compiler find them:

- Callers in `main` that ignore the id: `AddTransactionUseCase`,
  `MinusCsvService`, `HistoryViewModel` — no behavior change needed (Kotlin
  discards unused return values; just recompile).
- Fake implementations in tests: run
  `grep -rln "override suspend fun addTransaction" app/src/test app/src/androidTest`
  and update each fake to return a monotonic fake id (e.g. `++fakeIdCounter`),
  which also lets the new test assert on the returned id.

Do not change `addQueuedTransaction`, `upsertTransactions`, or any other
repository method.

### Step 4 — Test

In `app/src/test/java/com/sachit/moneypal/domain/usecase/ProcessIncomingSmsUseCaseTest.kt`
(exists; mockk + Truth; reuse its existing parse-able-body helpers):

1. `captured result carries the real inserted id` — mock `addTransaction` to
   return `123L`; assert `result is Captured` and `result.transactionId == 123L`.
2. `queued result keeps transactionId 0` — settings whose period ended before
   the SMS date; assert `Captured.transactionId == 0L` and
   `addQueuedTransaction` was called.

## Out of scope

- `AddTransactionUseCase` return-type changes (its callers don't need the id).
- `UndoSmsCaptureReceiver`, `NotificationHelper`, `SmsIngestWorker` — they
  already handle a real id correctly.
- Queued-transaction deletion UX.

## Test plan

See Step 4. The two new tests join the existing `ProcessIncomingSmsUseCaseTest`
suite.

## Done criteria (machine-checkable)

1. `grep -n "suspend fun addTransaction(transaction: Transaction): Long" app/src/main/java/com/sachit/moneypal/data/repository/BudgetRepository.kt` — 1 match.
2. `grep -n "transactionId = transaction.id" app/src/main/java/com/sachit/moneypal/domain/usecase/ProcessIncomingSmsUseCase.kt` — 0 matches.
3. `./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin` — exit 0 (catches any missed fake/caller).
4. `./gradlew :app:testFossDebugUnitTest` — exit 0 including the 2 new tests.

## Maintenance notes

- Any future ingestion path (notification listener, wear quick-add) that shows
  an undo/rollback affordance must use the returned id, not `transaction.id`.
- If a future plan makes `addTransaction` batch-insert, keep a single-row
  variant returning the id — undo depends on it.

## Escape hatches

- If the compiler surfaces implementors of `BudgetRepository` beyond the impl
  and test fakes, update them mechanically; if any need a behavioral decision
  beyond returning the id, STOP and report.
- If `TransactionDao.insert` does not return `Long` at execution time (it does
  today — verified), STOP and report.
