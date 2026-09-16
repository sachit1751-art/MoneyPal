# Plan 012 — SMS dedupe: atomic insert + bounded seen-store

**Status:** TODO
**Written against commit:** `ff6f773` (2026-09-16)
**Category:** correctness fix (audit findings #1, #2, #6)
**Depends on:** nothing
**Effort:** S · **Risk of fix:** low

## Why

`ProcessIncomingSmsUseCase` inserts the transaction and *then* marks the dedupe
key as seen, and returns `Result.Error` when anything throws. `SmsIngestWorker`
maps `Result.Error` to `Result.retry()`. So the exact failure order
"insert succeeds → `markSmsSeen` throws → worker retries" inserts the **same
expense twice**. Money duplication is the worst failure mode a budget app has.

Second problem: the seen-store is a DataStore boolean per key
(`sms_seen|sms|<sender>|<minute>|<amount>|<D>`), written on every capture and
**never pruned** — it grows forever.

Third: the period check uses `LocalDate.now()` although the SMS carries its own
timestamp; a message delivered after midnight on the 1st can land in the wrong
period.

## Current state (verified excerpts)

`app/src/main/java/com/sachit/moneypal/domain/usecase/ProcessIncomingSmsUseCase.kt`
(lines ~92–121):

```kotlin
val isPastPeriodEnd =
    budgetSettings != null && LocalDate.now().isAfter(budgetSettings.getPeriodEndDate())
...
if (isPastPeriodEnd) {
    budgetRepository.addQueuedTransaction(transaction)
    ...
} else {
    budgetRepository.addTransaction(transaction)
    ...
}

// Mark seen only after a successful insert (re-delivery re-inserts rather than loses).
settingsRepository.markSmsSeen(dedupeKey)
```

`app/src/main/java/com/sachit/moneypal/data/repository/SettingsRepositoryImpl.kt`
(lines 492–500):

```kotlin
override suspend fun isSmsSeen(key: String): Boolean {
    return dataStore.data.first()[booleanPreferencesKey(SMS_SEEN_PREFIX_KEY_NAME + key)] ?: false
}

override suspend fun markSmsSeen(key: String) {
    dataStore.edit { preferences ->
        preferences[booleanPreferencesKey(SMS_SEEN_PREFIX_KEY_NAME + key)] = true
    }
}
```

`app/src/main/java/com/sachit/moneypal/presentation/sms/SmsIngestWorker.kt`
(lines 46–62): `Result.Error -> Result.retry()`.

## Steps

### Step 1 — Make the dedupe write part of the capture transaction

In `ProcessIncomingSmsUseCase.invoke`, move the seen-mark *before* the insert
is considered final, and stop retrying on dedupe-mark failure:

```kotlin
// After the insert succeeded:
try {
    settingsRepository.markSmsSeen(dedupeKey)
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    // The expense IS recorded; failing the whole result would make the
    // worker retry and duplicate it. Log and swallow — worst case a redelivered
    // duplicate is caught by user undo, not silently double-charged.
    logcat(TAG) { "markSmsSeen failed after insert (not retrying): ${e.message}" }
}
return Result.Captured(...)
```

Also remove `markSmsSeen`/`isSmsSeen` failures from the path that maps to
`Result.Error`: `Result.Error` must be reserved for *insert* failures only
(those are safe to retry because nothing was inserted).

### Step 2 — Belt-and-braces guard inside `addTransaction` path (no retry on capture-success)

In `SmsIngestWorker.doWork`, change the `Captured` branch so WorkManager cannot
retry a completed capture: return `Result.success()` for `Captured` (it already
does), and change `Result.Error` handling to only retry when
`result.reason` starts with `"insert"`:

```kotlin
is ProcessIncomingSmsUseCase.Result.Error ->
    if (result.reason.startsWith("insert:")) Result.retry() else Result.success()
```

and in the use case, wrap **only** the repository insert/queue calls:

```kotlin
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    Result.Error("insert:${e.message ?: "Capture failed"}")
}
```

### Step 3 — Bound the seen-store

Replace the per-key boolean store with a bounded ring of the last N keys
(N = 500 is ample: minute-bucketed keys expire in usefulness within hours).

In `SettingsRepository` (interface), add:

```kotlin
/** Bounded dedupe memory for SMS captures (plan 012). */
suspend fun isSmsSeen(key: String): Boolean            // keep signature
suspend fun markSmsSeen(key: String)                   // keep signature
```

In `SettingsRepositoryImpl`, implement with a single string-set preference:

```kotlin
private val smsSeenCache = mutableSetOf<String>() // loaded lazily

override suspend fun isSmsSeen(key: String): Boolean {
    return loadSmsSeenSet().contains(key)
}

override suspend fun markSmsSeen(key: String) {
    val next = loadSmsSeenSet() + key
    val bounded = if (next.size > SMS_SEEN_MAX) next.drop(next.size - SMS_SEEN_MAX).toSet() else next
    dataStore.edit { prefs -> prefs[SMS_SEEN_SET_KEY] = bounded.toSet() }
    smsSeenCache.clear(); smsSeenCache.addAll(bounded)
}

private suspend fun loadSmsSeenSet(): Set<String> {
    if (smsSeenCache.isEmpty()) {
        smsSeenCache.addAll(dataStore.data.first()[SMS_SEEN_SET_KEY] ?: emptySet())
    }
    return smsSeenCache
}

companion object { ... private const val SMS_SEEN_MAX = 500 }
```

Declare `private val SMS_SEEN_SET_KEY = stringSetPreferencesKey("sms_seen_set")`
and **delete** the old `SMS_SEEN_PREFIX_KEY_NAME` constant + old keys are
simply abandoned (they are only read via the removed code path). Migration of
old keys is unnecessary: dedupe keys are minute-bucketed and transient.

Keep the public signatures on the `SettingsRepository` interface unchanged so
`ProcessIncomingSmsUseCase` compiles untouched.

### Step 4 — Use the SMS timestamp for the period check

In `ProcessIncomingSmsUseCase`, replace `LocalDate.now()` with the capture day
derived from the SMS timestamp:

```kotlin
val captureDate = Instant.ofEpochMilli(match.timestampMillis)
    .atZone(ZoneId.systemDefault()).toLocalDate()
val isPastPeriodEnd =
    budgetSettings != null && captureDate.isAfter(budgetSettings.getPeriodEndDate())
```

(The rest of the pipeline already uses the SMS timestamp for `eventTime`, so
this makes period assignment consistent with it.)

## Out of scope

- Parser changes (plan 013), merchant extraction (plan 014), review inbox (015).
- Any change to `BankSmsParser.dedupeKey` semantics (plan 013 keeps the key format).
- The `:wear` module and watch sync.

## Test plan

Extend `app/src/test/java/com/sachit/moneypal/domain/sms/BankSmsParserTest.kt`
is NOT where these go. Create
`app/src/test/java/com/sachit/moneypal/domain/usecase/ProcessIncomingSmsUseCaseTest.kt`
following the fake-repository pattern in
`app/src/test/java/com/sachit/moneypal/presentation/ui/budget/BudgetViewModelTest.kt`
(`mockk(relaxed = true)` for the repos):

1. `dedupe marked after insert failure is not marked seen` — make
   `addTransaction` throw; assert `markSmsSeen` never called.
2. `insert success then markSmsSeen failure still returns Captured` — make
   `markSmsSeen` throw; assert result is `Captured` (no `Error`).
3. `captureDate from sms timestamp decides queueing` — settings with period
   ended 2026-03-31, SMS timestamp 2026-04-02 → `addQueuedTransaction` called
   even though "now" is mocked earlier (pass a `today` provider or inject
   `Clock`; a small `clock: () -> LocalDate = { LocalDate.now() }` constructor
   param is the codebase-friendly way — search usages before changing).

## Done criteria (machine-checkable)

1. `./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin` — exit 0.
2. `./gradlew :app:testFossDebugUnitTest` — exit 0, including the 3 new tests.
3. `grep -n "SMS_SEEN_PREFIX_KEY_NAME" app/src/main/java/com/sachit/moneypal/data/repository/SettingsRepositoryImpl.kt` — no matches.
4. `grep -n "startsWith(\"insert:\")" app/src/main/java/com/sachit/moneypal/presentation/sms/SmsIngestWorker.kt` — 1 match.

## Maintenance notes

- If a future plan adds SMS ingestion from a second source (e.g. notification
  listener), it MUST go through `ProcessIncomingSmsUseCase` so the dedupe and
  the insert/error contract stay in one place.
- The 500-key bound is arbitrary but generous; if logs ever show legitimate
  misses, raise it — do not reintroduce per-key preferences.
