# Plan 025 — Restore: remap paid-occurrence + category ids

**Status:** TODO
**Written against commit:** `664cdc1` (2026-09-17)
**Category:** fix (data integrity)
**Depends on:** 028 (characterization tests must land first)
**Effort:** M · **Risk of fix:** medium (touches restore; safety net exists)

## Why

Backup files store device-local autoincrement ids. The restore path writes
them back verbatim, so on any fresh device (the main restore scenario) every
recurrence-occurrence mark and most category links point at rows that don't
exist — or worse, at an UNRELATED row that happens to reuse the id.

Verified specifics (`domain/usecase/RestoreBackupUseCase.kt` at `664cdc1`):

1. **Paid occurrences dangle.** Restored transactions are upserted with
   `id = 0L` → Room assigns NEW ids. But occurrences are then marked with the
   backup's original ids:
   ```kotlin
   budgetRepository.markRecurrentOccurrencePaid(
       occurrence.transactionId,                       // = old device id
       LocalDate.ofEpochDay(occurrence.occurrenceDateEpochDay),
   )
   ```
   → skip/paid suppression silently stops working after restore: already-paid
   monthly bills get re-notified and re-marked on wrong rows.
2. **Category ids go stale.** Restored transactions keep
   `backupTransaction.categoryId` whenever the comment-to-name heuristic
   fails:
   ```kotlin
   categoryId = backupTransaction.categoryId?.let { id ->
       val name = backupTransaction.comment.takeIf { c -> c.isNotBlank() }
       name?.let { categoryNameToId[it] } ?: id          // ← raw id kept
   } ?: backupTransaction.categoryId
   ```
   A stale id can collide with a DIFFERENT local category — transactions
   display and envelope under the wrong category.

## Current state (verified excerpts)

Repository upsert (void — no ids returned):
```kotlin
override suspend fun upsertTransactions(transactions: List<Transaction>) {
    val entities = transactions.map { it.toEntity() }
    transactionDao.insertAllOrReplace(entities)
}
```
DAO:
```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertAllOrReplace(transactions: List<TransactionEntity>)
```
(`@Insert` variants CAN return `List<Long>` row ids, aligned with input order
— that is the hook this plan uses.)

Restore's existence checks return booleans only:
```kotlin
val exists = when {
    !clientGeneratedId.isNullOrBlank() ->
        budgetRepository.existsTransactionByClientGeneratedId(clientGeneratedId)
    else -> budgetRepository.getAllTransactionsIncludingDeleted().any { ...triple... }
}
```

Occurrence loop and category map as quoted above. `RestoreResult`
(`data/backup/BackupModels.kt`) counts what the UI displays
(`BackupTransferManager` renders the summary; add a field with a default and
it compiles everywhere).

## Steps

### Step 1 — Repository: upsert returns row ids; add id lookup by clientGeneratedId

`BudgetRepository` interface + impl:
```kotlin
/** @return row ids aligned with the input list (post-upsert). */
suspend fun upsertTransactions(transactions: List<Transaction>): List<Long>
```
```kotlin
override suspend fun upsertTransactions(transactions: List<Transaction>): List<Long> =
    transactionDao.insertAllOrReplace(transactions.map { it.toEntity() })
```
DAO gains the return type:
```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertAllOrReplace(transactions: List<TransactionEntity>): List<Long>
```
Also add:
```kotlin
@Query("SELECT id FROM transactions WHERE clientGeneratedId = :clientGeneratedId LIMIT 1")
suspend fun findIdByClientGeneratedId(clientGeneratedId: String): Long?
```
exposed as `suspend fun findTransactionIdByClientGeneratedId(id: String): Long?`
on the repository. Let the compiler list all `upsertTransactions` callers
(`grep -rn "upsertTransactions(" app/src --include="*.kt"` — verified:
restore + possibly the settings/CSV paths) and update fakes in tests to
return `input.map { it.id.takeIf { v -> v != 0L } ?: nextFakeId() }`.

### Step 2 — RestoreBackupUseCase: build the id map, then remap

Restructure the transaction loop minimally (keep all dedupe semantics
identical):

- Track per backup transaction: the decision (restored-new / skipped-existing)
  AND the resolved local row id:
  - restored-new → take the corresponding element of the `upsertTransactions`
    return list;
  - skipped by clientGeneratedId → `findTransactionIdByClientGeneratedId(...)`;
  - skipped by triple-match → make the triple scan return the matched row's id
    (change `.any { ... }` to `firstOrNull { ... }?.id` — same predicate).
- Build `backupIdToLocalId: Map<Long, Long>` from `backupTransaction.id →
  resolvedLocalId` for every entry with `id > 0`.
- **Occurrences** — replace the verbatim marking:
  ```kotlin
  val localId = backupIdToLocalId[occurrence.transactionId]
  if (localId == null) {
      paidOccurrencesSkipped++
      continue // cannot resolve; marking a dangling id corrupts data
  }
  if (occurrence.paidAt == SKIPPED_OCCURRENCE_MARKER) {
      budgetRepository.markOccurrenceSkipped(localId, ...)
  } else {
      budgetRepository.markRecurrentOccurrencePaid(localId, ...)
  }
  paidOccurrencesRestored++
  ```
  Unresolvable occurrences are SKIPPED (logged with `logcat`), never marked
  against a guessed id.
- **Category ids** — replace the `?: id` fallback with a drop:
  ```kotlin
  categoryId = backupTransaction.categoryId?.let { id ->
      val name = backupTransaction.comment.takeIf { c -> c.isNotBlank() }
      name?.let { categoryNameToId[it] }          // null when unmapped
  }   // stale ids are dropped, never passed through
  ```
  Losing a category link on a restored row beats linking the wrong category;
  the transaction itself is intact and recategorization is one tap.
- **RestoreResult** — add `paidOccurrencesSkipped: Int = 0` (defaults keep
  every existing constructor call compiling) and surface it in the restore
  summary UI ONLY if that is a one-line string-resource addition; otherwise
  leave the UI alone and note it in the commit message.

### Step 3 — Update the characterization tests marked PLAN-025 and add new cases

In `RestoreBackupUseCaseTest.kt` (from plan 028):

- Rewrite test 5 (`paid occurrences marked with backup transactionId`) into:
  `occurrence remapped to the restored row id` — fresh install, backup tx
  `id = 42` (occurrence references 42) → after restore,
  `markRecurrentOccurrencePaid` called with the id returned by the (fake)
  upsert, not 42.
- Add `unresolvable occurrence is skipped, not marked` — occurrence
  referencing a backup id that was never in the transaction list →
  `markOccurrenceSkipped`/`markRecurrentOccurrencePaid` NOT called,
  `paidOccurrencesSkipped == 1`.
- Rewrite test 6 (`keeps raw categoryId`) into: unmapped comment → restored
  row `categoryId == null`.
- Add `occurrence maps to the pre-existing row when deduped by clientGeneratedId`
  — duplicate-by-cgid skip → occurrence uses the EXISTING local id.
- Tests 1-4, 7-12 must pass UNMODIFIED (regression guards).

### Step 4 — Gates

Full compile + unit suite; the existing androidTest migration/repository
tests are untouched by this plan but run them if the emulator gate is part
of the release ritual.

## Out of scope

- Any change to the backup FILE format (`schemaVersion` stays 1; ids were
  already in the file — they just gain meaning as "source-device ids").
- `CreateBackupUseCase` (export side is correct).
- Archived-budget `periodId` remapping (archives are keyed by periodId, which
  is stable across devices — verified semantics; do not touch).
- Wear/watch code.

## Test plan

Plan 028's suite + the new/updated cases in Step 3. Instrumented round-trip
optional: backup → wipe → restore on an in-memory Room DB, assert occurrence
marks point at real ids.

## Done criteria (machine-checkable)

1. `grep -n "List<Long>" app/src/main/java/com/sachit/moneypal/data/repository/BudgetRepository.kt` — ≥ 1 (upsertTransactions).
2. `grep -n "backupIdToLocalId\|paidOccurrencesSkipped" app/src/main/java/com/sachit/moneypal/domain/usecase/RestoreBackupUseCase.kt` — ≥ 2 matches.
3. `grep -n "?: id" app/src/main/java/com/sachit/moneypal/domain/usecase/RestoreBackupUseCase.kt` — 0 matches inside the categoryId mapping.
4. `./gradlew :app:testFossDebugUnitTest` — exit 0 including the full RestoreBackupUseCaseTest suite.

## Maintenance notes

- Any future backup field carrying a local id (new tables, new link columns)
  must ship with a remap in this use case and a map entry here — the pattern
  is `backupIdToLocalId`.
- `REPLACE`-conflict upserts assign a NEW rowId to a replaced row; the
  returned-list alignment already accounts for that. If the upsert strategy
  ever changes to `IGNORE`/`ABORT`, re-verify the id map.

## Escape hatches

- If returning `List<Long>` from `insertAllOrReplace` breaks another caller
  in a way that needs a product decision (not a mechanical fix), STOP and
  report.
- If the triple-match scan cannot return ids without changing dedupe
  semantics, STOP and report — do not weaken the dedupe predicate to make
  remapping easier.
