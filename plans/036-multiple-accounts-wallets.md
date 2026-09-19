# Plan 036 — Multiple accounts/wallets with transfers

**Status:** TODO
**Written against commit:** `467ce9d` (2026-09-19) — round 5 (features)
**Category:** feature (large, structural)
**Depends on:** round-4 plan 028 (restore characterization tests) MUST land
first — this plan touches backup/restore and migration; 025 (restore id
integrity) should land first too, same reason
**Effort:** L · **Risk of fix:** medium (Room migration + backup format +
every read path)

## Why

Cash, one bank card, and a credit card behave differently (reimbursements,
cutoffs, balances) but MoneyPal stores every transaction in one undifferentiated
bucket with only a `paymentMethod` label. Multiple accounts let users filter,
see per-account balances, and move money between wallets — the most requested
structural feature in budget apps. This is the one L-effort plan in the slate;
it is specified end-to-end so the executor never has to make schema decisions.

## Conventions (every round-5 plan)

- User-facing strings in `app/src/main/res/values/strings.xml` **plus
  `values-es` and `values-fr`** (other locales are Crowdin-managed).
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

- No account entity anywhere (`grep AccountEntity|WalletEntity` → 0).
- `Transaction` (`domain/model/TransactionModel.kt`) has no account field;
  entity + schema 23 (`app/schemas/...23.json`) confirm the columns.
- Database is **version 23**; migrations live in
  `data/local/AppDatabaseMigrations.kt` and exported schemas in
  `app/schemas/com.sachit.moneypal.data.local.AppDatabase/`. AGENTS.md warns:
  if the DB package ever changes, schema JSONs must move too.
- Payment methods: `PaymentMethod` enum (`TransactionModel.kt` line 118:
  CASH/CARD/OTHER — read it). Accounts are orthogonal to payment method;
  do not merge the concepts.
- Backup format: `data/backup/BackupModels.kt` v1 JSON with per-field
  tolerant restore (`RestoreBackupUseCase` uses try/catch enum parsing, e.g.
  lines 131-134). Restore inserts with remapping concerns handled by plan 025.
- Duplicate-check helpers: `TransactionDao.existsByClientGeneratedId`
  (line ~90) — transfers must set deterministic `clientGeneratedId`s.

## Steps

### Step 1 — Schema (migration 23 → 24, one migration per train)

1. New entity `AccountEntity` (`data/local/entity/AccountEntity.kt`,
   pattern: `CategoryEntity.kt`): `id` PK autoincrement, `name` TEXT NOT NULL,
   `emoji` TEXT?, `colorArgb` TEXT?, `archived` INTEGER NOT NULL DEFAULT 0,
   `sortOrder` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL.
2. `TransactionEntity`: add `accountId` INTEGER (nullable — null = legacy
   "default account"). Add a named index on `accountId`.
3. Bump `AppDatabase` to version 24; write `MIGRATION_23_24`:
   `CREATE TABLE accounts...` + `ALTER TABLE transactions ADD COLUMN accountId
   INTEGER DEFAULT NULL`. Export schema 24 JSON (build generates it; commit
   it).
4. `AppDatabaseMigrationTest`: add 23→24 with pre-populated v23 data
   (pattern: existing migration tests).
5. Seed a single "Default" account row in the migration so existing rows can
   backfill `accountId = 1` via `UPDATE transactions SET accountId = 1 WHERE
   accountId IS NULL`... **Decision (binding):** keep `accountId` nullable and
   treat NULL as the default account; do NOT backfill. Null stays the
   "uncategorized account" and avoids a rewrite of 10k-row tables.

### Step 2 — Domain model + repository

- `domain/model/Account.kt` (mirror `Category.kt` shape) + mapping in
  `BudgetRepositoryImpl`.
- `Transaction` gains `accountId: Long? = null` (companion `create` default
  null).
- Repository: CRUD for accounts (archive, not delete, when transactions
  reference them); `getTransactionsForAccount(id: Long?)` filter support.
  **Archived accounts stay resolvable** — restore and history must never
  crash on a dangling id.

### Step 3 — Transfer semantics

- New `TransferUseCase`: a transfer of X from account A to account B inserts
  TWO rows: expense X (accountId=A) + income X (`isIncome=true`, accountId=B),
  both sharing a new UUID `clientGeneratedId` pair with a shared
  `transferGroupId`-style marker. **Binding decision:** encode the pair via
  `sourceTransactionId` pointing from income row → expense row (field exists,
  `TransactionModel.kt` line 33) — no new column. Deletion logic: deleting a
  transfer leg offers "delete both" (UI confirm).
- Exclusion rules (add tests): transfers and their income legs are excluded
  from category spend, envelope progress, no-spend streaks, and burn-rate —
  mirror how `IncomeExclusionTest.kt` excludes income.

### Step 4 — UI

- Account picker: a chip row in the numpad/editor (pattern:
  `PaymentMethodChipRow` in `presentation/ui/editor/Editor.kt` ~line 985)
  defaulting to last-used (DataStore, pattern: `selectedPaymentMethod`).
- Settings → "Accounts" manager screen (CRUD, archive, reorder);
  navigation per existing settings sub-screen pattern.
- History filter: account chips alongside existing payment-method filter
  (`HistorySearchBar.kt` lines ~124-129).
- Balance display: per-account net (income − expense) on the manager screen
  only — v1 does NOT put balances on the main screen (scope control).

### Step 5 — Backup/restore

- `BackupModels.kt`: add optional `accounts` array + optional `accountId` on
  backup transactions. Restore: create-or-match accounts **by name** (case
  -insensitive trim), remap ids (plan 025's remapping pattern), missing/
  unknown accountId → null (default). Restore of backups WITHOUT accounts
  must be byte-identical in behavior to today (plan 028's tests prove it).

### Step 6 — SMS + Wear ingestion compatibility

- `ProcessIncomingSmsUseCase`/`QuickAddExpenseWriter`/Wear ingestor set
  accountId = user's "default capture account" setting (new DataStore key,
  nullable → null). Wear payloads are NOT extended this round
  (maintainer exclusion on wear) — wear-sourced rows get the default.

## Out of scope

- Per-account currencies (single-currency budget stays; conversion is a
  different feature).
- Reconciling/bank syncing, account numbers, institution logos.
- Balances on widgets or watch; account-scoped budgets.
- Deleting accounts outright (archive only).

## Test plan

- `MIGRATION_23_24` in `AppDatabaseMigrationTest` (v23 data → v24, default
  rows intact).
- `TransferUseCaseTest` (androidTest, Room-in-memory, pattern:
  `MarkRecurrentOccurrencePaidIntegrationTest`): pair insertion, shared
  marker, delete-both path.
- Domain: exclusion tests (envelope/streak/insight ignore transfer legs) in
  the respective calculator test files.
- Restore: extend plan 028's characterization suite with an account-bearing
  backup + a pre-account backup (both restore cleanly).
- Repository: archive flow hides from picker but resolves in history.

## Done criteria (machine-checkable)

1. `./gradlew :app:testFossDebugUnitTest` + `:sync-contract:test` — exit 0.
2. `./gradlew :app:connectedFossDebugAndroidTest` — exit 0 (device available;
   otherwise note skipped and land only unit-level proof).
3. Schema file `app/schemas/.../24.json` exists and is committed.
4. Manual: create 3 accounts, record spends in 2, transfer 100 between them →
   both legs appear, category charts unchanged by the transfer, backup →
   wipe → restore reproduces accounts + assignments.

## Maintenance notes

- `accountId` nullable-with-null-default is THE load-bearing decision; every
  future feature (widgets, wear, reports) must treat null as valid.
- The transfer pair marker via `sourceTransactionId` is a v1 encoding; if a
  future plan needs multi-leg transactions, introduce a real column then.

## Escape hatches

- If Room's auto-migration generation fails on the index or the schema JSON
  export misbehaves (AGENTS.md documents this failure mode), STOP and report
  with the exact Gradle error.
- If plan 028's characterization tests reveal restore paths that would break
  with the optional-fields approach, STOP and reconcile with that plan's
  author notes in its maintenance section.
