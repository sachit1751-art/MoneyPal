# Plan 043 — Cash + wallet balance tracker

**Status:** DONE — `7eca5b9` (Room 23→24 with AutoMigration, schemas committed)
**Written against commit:** `48bb35b` (2026-09-23)
**Category:** feature (medium)
**Depends on:** — (no schema collision with 044; if both land, 044 lands first)
**Effort:** M · **Risk:** medium (new Room table, but no migration risk)

## Why

`PaymentMethod.CASH/CARD/OTHER` exists but is purely cosmetic. Users who want
to "track my wallet" have no anchor: the app knows spending but not
*remaining* cash. A lightweight balance — **not** a full multi-account system
(the round-5 plan 036 for that was intentionally dropped) — gives cash users a
closing loop: set a starting balance, every cash expense decrements it,
adjustments re-sync it.

## Current state (verified)

- `Transaction` (`domain/model/TransactionModel.kt`) has
  `paymentMethod: PaymentMethod` (`CASH`, `CARD`, `OTHER`), `isIncome`,
  `isAdjustment` (adjustments cannot be income — enforced in `create`).
- Room is at **version 23** (`AppDatabase.kt`), schemas exported under
  `app/schemas/`; auto-migrations 16→…→23 registered. **Adding a table:
  bump to 24 with `AutoMigration(from = 23, to = 24)`** and commit the
  exported `23.json` + `24.json`.
- `PaymentMethod` values are persisted as strings on
  `TransactionEntity.paymentMethod` (backup `BackupTransaction` line 54
  defaults `"OTHER"`).
- No balance/bank/wallet entity exists (`grep -rn "balance" data/local/entity`
  → none).

## Steps

1. **Room 23→24**: new `WalletBalanceEntity` (`id = 1` singleton row:
   `startingBalance` TEXT (BigDecimal plain string, nullable until first
   setup), `updatedAt` INTEGER). Auto-migration; export schemas; extend
   `AppDatabaseMigrationTest` pattern in `androidTest`.
2. **Domain**: `CashBalanceCalculator` — pure function
   `currentBalance(starting: BigDecimal?, cashTransactions: List<Transaction>): BigDecimal?`
   where cash txs are `paymentMethod == CASH && !isDeleted`, spend subtracts,
   `isIncome` adds, `isAdjustment` **replaces** the running total with the
   adjustment amount (document this choice in KDoc). Null starting → null.
   JUnit4+Truth tests incl. adjustment semantics.
3. **Repository/API**: `WalletBalanceRepository` (DataStore-free; Room-backed)
   with `observeBalance()` producing the calculated value; inject into the
   budget/analytics flows that need it.
4. **UI**: a compact "Cash on hand" card in Analytics (section list at
   `Analytics.kt` ~lines 215–231: insert after Savings (5)) + a
   "set starting balance / re-sync via adjustment" flow in Settings or the
   card's overflow. Strings in three locales.
5. **Numpad hint** (optional, small): when the entered payment method is
   CASH and a starting balance exists, show the post-transaction balance on
   the confirmation row. Keep it read-only.

## Out of scope

- Multiple wallets/accounts, transfers, card balances (dropped round-5 plan
  036 stays dropped), wear.

## Test plan

- `CashBalanceCalculatorTest`: empty ledger, income+expense mix, adjustment
  reset semantics, CASH-only filtering (CARD rows ignored).
- Room migration test for 23→24 (androidTest pattern:
  `AppDatabaseMigrationTest`).
- ViewModel test for the card state.

## Done criteria

1. `AppDatabase` `version = 24` with `AutoMigration(23, 24)`; schemas
   committed.
2. Verification gate exit 0 including new tests.

## Escape hatches

- If adjustment semantics turn out ambiguous in real data, report before
  inventing behavior; fall back to "adjustment adds like income with a
  distinct label" only with maintainer sign-off.
