# Plan 028 — Characterization tests for backup/restore

**Status:** TODO
**Written against commit:** `664cdc1` (2026-09-17)
**Category:** tests (safety net — lands FIRST)
**Depends on:** nothing
**Effort:** M · **Risk of fix:** none (tests only)

## Why

Plan 025 changes how `RestoreBackupUseCase` handles ids (paid-occurrence
remap, category-id resolution). Before touching it, current behavior must be
pinned by tests so the refactor cannot silently change what it must not
change. Verified: **no test exists for restore today**
(`find app/src/test -iname "*restore*" -o -iname "*createbackup*"` → only
`BackupCodecTest`/`BackupEncryptionTest` and unrelated files), although the
restore path is one of the most data-destructive surfaces in the app.

Characterization style: each test asserts what the code DOES today (including
known-buggy behavior), not what it should do. Tests that pin behavior plan
025 will deliberately change are annotated `// PLAN-025: pinned-to-change` —
plan 025 updates those and adds its own.

## Current state (verified excerpts)

`RestoreBackupUseCase` (`domain/usecase/RestoreBackupUseCase.kt`) is a plain
class with constructor injection — JVM-testable with mockk, following the
fake-repository pattern in
`app/src/test/java/com/sachit/moneypal/presentation/ui/budget/BudgetViewModelTest.kt`
(`mockk(relaxed = true)` + captured slots):

```kotlin
class RestoreBackupUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
)
```

Behavior to pin (all verified in source):

- Replace-upsert restore: never deletes local rows; skips duplicates by
  `clientGeneratedId` when present, else by `(date, amount, comment)` triple.
- Categories restore first, matched by name; unchanged categories (same
  usageCount + isHidden) are skipped, changed ones upserted with the
  EXISTING id preserved (`id = existing?.id ?: 0L`).
- Transaction `categoryId` is "resolved" via
  `categoryNameToId[backupTransaction.comment]` — the comment-as-category-name
  heuristic; when the comment is blank or unmapped it keeps the backup's raw
  id (stale on another device). `// PLAN-025: pinned-to-change`
- Paid occurrences are marked with the backup's ORIGINAL transactionId —
  dangling on a fresh device. `// PLAN-025: pinned-to-change`
- Budget settings restored ONLY when local settings are null.
- Enum fields fall back to defaults on invalid values (MONTHLY, STATIC, OTHER,
  SYSTEM, BALANCED, ...).
- `RestoreResult` counts returned to the UI.

Backup models: all `@Serializable` with defaults
(`data/backup/BackupModels.kt`) — easy fixture builders.

## Steps

### Step 1 — Create the test file

`app/src/test/java/com/sachit/moneypal/domain/usecase/RestoreBackupUseCaseTest.kt`.
Dependencies: JUnit4 + Truth + mockk (all already used in this test source
set). Stub `BudgetRepository` and `SettingsRepository` with
`mockk(relaxed = true)`; capture upserts with
`MutableList` + `coEvery { ... } answers { ... }` slots. Suspend calls run
under `kotlinx.coroutines.test.runTest` (check an existing use-case test for
the exact runner idiom — `ProcessIncomingSmsUseCaseTest.kt` uses it).

Fixture helper:
```kotlin
private fun backupTx(
    id: Long = 0,
    amount: String = "10.00",
    comment: String = "Coffee",
    date: Long = 1_700_000_000_000,
    clientGeneratedId: String? = null,
    categoryId: Long? = null,
) = BackupTransaction(
    id = id, amount = amount, comment = comment, date = date,
    clientGeneratedId = clientGeneratedId, categoryId = categoryId,
)
```

### Step 2 — Write the characterization tests

1. `fresh install restores all transactions with id 0` — local history empty;
   2 backup txs → `upsertTransactions` captured both, each with `id == 0`;
   `RestoreResult.transactionsRestored == 2`.
2. `duplicate clientGeneratedId is skipped` —
   `existsTransactionByClientGeneratedId` returns true → skipped,
   `transactionsSkipped == 1`, nothing upserted.
3. `triple-match duplicate is skipped` — no clientGeneratedId;
   `getAllTransactionsIncludingDeleted` returns a row with same
   date/amount/comment → skipped.
4. `categories unchanged are skipped, changed preserve id` — local category
   "Food" (usageCount 3, id 7); backup "Food" usageCount 3 → no upsert.
   Backup "Food" usageCount 9 → upserted with `id == 7`.
5. `PLAN-025: paid occurrences marked with backup transactionId` — backup
   occurrence `transactionId = 42` → `markRecurrentOccurrencePaid(42, date)`
   called verbatim (documents current dangling-id behavior).
6. `PLAN-025: transaction keeps raw categoryId when comment unmapped` —
   backup tx `categoryId = 99`, blank comment → upserted row has
   `categoryId == 99` (documents current stale-id behavior).
7. `transaction categoryId resolved from comment when mapped` — comment
   "Food", `categoryNameToId` has Food→7 → upserted `categoryId == 7`.
8. `budget settings not overwritten when local exists` —
   `getBudgetSettingsSync` returns non-null → `saveBudgetSettings` not called,
   `budgetSettingsRestored == false`.
9. `budget settings restored on fresh install` — null local → saved with
   parsed values, `budgetSettingsRestored == true`.
10. `invalid enums fall back to defaults` — backup with
    `period = "NOT_A_REAL_ENUM"`, `paymentMethod = "JUNK"` → saved settings
    `period == MONTHLY`, upserted tx `paymentMethod == OTHER`.
11. `settings block applies theme + savings` — verify `setThemeMode(DARK)`
    (or the fixture's value) and `setSavingsPreferences` with parsed
    BigDecimal goal.
12. `empty backup yields all-zero result and no repo writes` — default
    `MoneyPalBackup(exportedAtEpochMs = 0)` → zero counts, `upsertTransactions`
    not called.

### Step 3 — Run and freeze

`./gradlew :app:testFossDebugUnitTest --tests "*RestoreBackupUseCaseTest"` —
all green. If a test FAILS because the production code crashes (exception)
rather than producing the documented behavior, STOP and report — a crash is a
new finding, not a characterization target.

## Out of scope

- ANY change to `app/src/main` sources. This plan is read-only on production
  code. If you find yourself editing main sources, you have drifted into
  plan 025 — stop.
- Backup file format changes, `BackupCodec` tests (already exist).

## Test plan

This plan IS the test plan.

## Done criteria (machine-checkable)

1. `find app/src/test -name "RestoreBackupUseCaseTest.kt"` — 1 file.
2. `./gradlew :app:testFossDebugUnitTest --tests "*RestoreBackupUseCaseTest"` — exit 0.
3. `git diff --stat HEAD -- app/src/main` — empty (no production changes).
4. The two `PLAN-025: pinned-to-change` annotations exist (grep).

## Maintenance notes

- When plan 025 lands, it MUST update tests 5 and 6 (the pinned-to-change
  ones) and may freely add cases; tests 1-4, 7-12 are regression guards and
  should keep passing unmodified.
- If `BudgetRepository`'s interface grows a method later, the relaxed mock
  absorbs it; only re-verify the captured-slot stubs.

## Escape hatches

- If mockk's relaxed stubbing can't satisfy a new interface method in a way
  that keeps tests deterministic, switch the two fakes to hand-rolled
  implementations of only the used methods — still no production changes.
