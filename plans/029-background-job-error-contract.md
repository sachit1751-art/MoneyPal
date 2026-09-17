# Plan 029 — Workers: bounded retries + correct completion results

**Status:** TODO
**Written against commit:** `664cdc1` (2026-09-17)
**Category:** fix (reliability of background jobs)
**Depends on:** nothing
**Effort:** M · **Risk of fix:** low

## Why

Three background-job correctness issues, all verified at `664cdc1`. They are
grouped in one plan because they share one theme — the `Result` (retry/failure)
contract — and each alone is too small for a plan.

1. **`AutoBackupWorker` treats "not due" as retryable failure.**
   `AutoBackupWorker.doWork` returns
   `return if (scheduler.runNow()) Result.success() else Result.retry()`.
   `runNow()` returns false in three very different cases: disabled/no folder
   (config), cadence not yet elapsed (normal, expected until the 15-day tick),
   and write failure (real failure — but `writeBackup` already caught it and
   returned false). For a `PeriodicWorkRequestBuilder(15, DAYS)` schedule,
   `Result.retry()` applies backoff AND, per WorkManager semantics, a retry
   resets the periodic interval measurement — so a user who disables
   auto-backup still leaves the worker spinning retries with exponential
   backoff forever. "Not due" must be `Result.success()`.

2. **`RecurrentExpenseNotificationWorker` returns `Result.failure()` on any
   exception.** Unlike retry, failure is terminal: WorkManager will not run
   this work again under this work-species until something re-enqueues it.
   The worker is enqueued per-transaction (`scheduleRecurrentExpenseNotification`),
   so a transient DB error on due-day silently kills the reminder for that
   bill — the user misses the notification MoneyPal promised. Transient
   errors should `Result.retry()` (WorkManager default backoff), and only
   known-permanent conditions should fail/succeed terminally.

3. **None of the workers rethrow `CancellationException`.** WorkManager
   cancels workers by coroutine cancellation; catching `Exception` and
   returning `Result.retry()`/`failure()` swallows the cancellation signal.
   WorkManager still stops the coroutine (it's structured), but the returned
   result can then be recorded for an already-cancelled work — and the
   codebase's own convention (`ProcessIncomingSmsUseCase`,
   `SmsIngestWorker`) is `if (e is kotlinx.coroutines.CancellationException) throw e`.
   Apply the same contract to all workers.

## Current state (verified excerpts)

`app/src/main/java/com/sachit/moneypal/presentation/notification/AutoBackupWorker.kt` (lines ~46-50):
```kotlin
override suspend fun doWork(): Result {
    val scheduler = EntryPointAccessors.fromApplication(
        applicationContext,
        AutoBackupEntryPoint::class.java,
    ).autoBackupScheduler()
    return if (scheduler.runNow()) Result.success() else Result.retry()
}
```

`AutoBackupScheduler.runNow()` (verified): returns false when disabled/blank
tree URI/cadence not reached (`shouldRunNow`) AND when `writeBackup` fails
(its own try/catch returns false). The false-cases are indistinguishable at
the worker.

`RecurrentExpenseNotificationWorker.doWork` (lines ~86-122, abridged):
```kotlin
return try {
    ...
    return Result.success()
    ...
    Result.success()
} catch (e: Exception) {
    logcat { "Error in RecurrentExpenseNotificationWorker\n${e.asLog()}" }
    Result.failure()
}
```
Note: `notifyRecurrentTransactionIfDue` already handles "not due today" and
"already marked" as normal `return` paths (not exceptions) — those are fine.

Also in the worker file family (same contract, same fix shape):
- `WeeklyDigestWorker`, `RefundNudgeWorker` — read them first; apply the same
  catch-shape if they swallow CancellationException or misuse failure/retry.
- `SmsIngestWorker` — already correct (its `Result.retry()` on
  `insert:`-prefixed errors and `success()` otherwise is the model; verified).

## Steps

### Step 1 — AutoBackupWorker: three-way outcome

Give the scheduler an explicit outcome instead of a boolean. In
`AutoBackupScheduler`:

```kotlin
enum class BackupOutcome { RAN, NOT_DUE, FAILED }

suspend fun runNow(): BackupOutcome {
    val settings = settingsRepository.observeSettings().first()
    if (!shouldRunNow(
            enabled = settings.autoBackupEnabled,
            treeUri = settings.autoBackupTreeUri,
            lastRunAt = settings.autoBackupLastRunAt,
            now = Instant.now(),
        )
    ) {
        return BackupOutcome.NOT_DUE
    }
    return if (writeBackup(settings.autoBackupTreeUri)) BackupOutcome.RAN else BackupOutcome.FAILED
}
```

Keep `forceRun(): Boolean` as a thin wrapper
(`writeBackup(...)` — behavior unchanged; read its current body before editing).

Worker:
```kotlin
override suspend fun doWork(): Result {
    val scheduler = EntryPointAccessors.fromApplication(
        applicationContext,
        AutoBackupEntryPoint::class.java,
    ).autoBackupScheduler()
    return try {
        when (scheduler.runNow()) {
            AutoBackupScheduler.BackupOutcome.RAN -> Result.success()
            AutoBackupScheduler.BackupOutcome.NOT_DUE -> Result.success()
            AutoBackupScheduler.BackupOutcome.FAILED ->
                if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
```

`FAILED` gets bounded retries (3) — SAF writes can fail transiently
(provider busy, storage unmounted) — then gives up until the next periodic
tick. The periodic schedule itself is untouched; `runNow()` still gates on
cadence, so a retry after a successful write can never double-write
(`lastRunAt` is set inside `writeBackup` on success).

`AutoBackupWorker` currently has no `runAttemptCount` use and no test; the
`shouldRunNow` decision function is unit-tested (find the test with
`grep -rln "shouldRunNow" app/src/test` — verified to exist) and must keep
passing unchanged.

### Step 2 — RecurrentExpenseNotificationWorker: retry transient, keep terminal paths

In the catch block:
```kotlin
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    logcat { "Error in RecurrentExpenseNotificationWorker\n${e.asLog()}" }
    if (runAttemptCount < 3) Result.retry() else Result.failure()
}
```
Everything inside the `try` stays as-is: the "no settings", "transaction
missing", "not due today", "already marked" paths already return
`Result.success()` — they are correct terminal outcomes.

### Step 3 — Sweep the remaining workers for the same catch shape

For each file in `app/src/main/java/com/sachit/moneypal/presentation/notification/`
and `data/csv/` containing `catch (e: Exception)` inside a `CoroutineWorker`
(read them; do not blind-edit): insert the CancellationException rethrow as
the first statement. Apply the retry/failure distinction only where the
current code demonstrably misuses it (failure on something transient);
otherwise leave semantics alone. Expected touchpoints besides the two above:
`WeeklyDigestWorker`, `RefundNudgeWorker`, `CsvImportWorker`
(`CsvImportWorker` uses `runCatching{}.getOrElse` — convert to explicit
try/catch so the rethrow can be added).

### Step 4 — Tests

- Extend the existing `shouldRunNow` test file with a pure
  `AutoBackupScheduler.BackupOutcome` mapping test if the mapping is
  extractable without Android types; otherwise skip (the enum mapping lives
  in the worker, which is not JVM-testable) and cover it via done-criteria
  review instead.
- No new tests for the catch rethrow (untestable on JVM; verified by review).

## Out of scope

- Scheduling cadences, notification content, `WorkManager` policies.
- `SmsIngestWorker` (already correct — do not touch except reading).
- The `EntryPointAccessors` DI pattern.

## Test plan

See Step 4. Primary verification is the suite staying green plus the
done-criteria greps.

## Done criteria (machine-checkable)

1. `grep -n "Result.retry()" app/src/main/java/com/sachit/moneypal/presentation/notification/AutoBackupWorker.kt` — matches exist ONLY inside the FAILED/exception branches guarded by `runAttemptCount < 3`.
2. `grep -c "NOT_DUE" app/src/main/java/com/sachit/moneypal/presentation/notification/AutoBackupScheduler.kt` — ≥ 1.
3. `grep -rn "CancellationException" app/src/main/java/com/sachit/moneypal/presentation/notification/ app/src/main/java/com/sachit/moneypal/data/csv/` — ≥ 4 files.
4. `./gradlew :app:compileFossDebugKotlin` — exit 0.
5. `./gradlew :app:testFossDebugUnitTest` — exit 0 (existing `shouldRunNow` tests unchanged and passing).

## Maintenance notes

- New workers must follow the `SmsIngestWorker` result contract: success for
  expected no-ops, retry only for transient failures, failure only for
  permanent ones, always rethrow CancellationException first in catch blocks.
- If WorkManager is ever bumped past 2.9, re-check
  `runAttemptCount` semantics for periodic work retries.

## Escape hatches

- If changing `runNow()`'s return type breaks a caller the audit did not see
  (grep `runNow(` across the repo — verified callers are `AutoBackupWorker`
  and tests only), STOP and report.
- If `runAttemptCount` is unavailable in some worker context (it is a
  `CoroutineWorker` property everywhere here), STOP and report.
