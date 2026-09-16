# Plan 017 — Refund tracker completion flow

**Status:** TODO
**Written against commit:** `ff6f773` (2026-09-16)
**Category:** feature
**Depends on:** nothing
**Effort:** S–M · **Risk:** low (schema already exists — this is the missing UX)

## Why

The schema already stores refund intent (`refundExpected`, `refundedAt` on
`TransactionEntity`, added in migration 19) but there is **no UI to mark a
refund as received** — rows stay pending forever and neither the budget nor
analytics ever shows "money that should come back". This plan ships the
completion loop: pending list, mark-received action, budget credit, and a
weekly nudge.

## Current state (verified)

- `TransactionEntity`: `refundExpected: Boolean`, `refundedAt: Long?`
  (defaults exported in schemas 19–22).
- Domain model `Transaction` already carries both fields
  (`TransactionModel.kt:26` region) and backup round-trips them
  (`BackupModels.kt:54` region).
- `ProcessIncomingSmsUseCase` inserts **credit** SMS as adjustments — a refund
  SMS is currently indistinguishable from any other credit (part of why this
  plan's Step 4 matters).
- `WeeklyDigestWorker`/`WeeklyDigestScheduler`
  (`presentation/notification/`) is the established pattern for optional
  periodic notifications (plan 003).

## Steps

### Step 1 — Pending-refunds query

`TransactionDao`:

```kotlin
@Query("SELECT * FROM transactions WHERE refundExpected = 1 AND refundedAt IS NULL " +
       "AND isDeleted = 0 ORDER BY date DESC")
fun observePendingRefunds(): Flow<List<TransactionEntity>>
```

`BudgetRepository`/Impl: `fun observePendingRefunds(): Flow<List<Transaction>>`
and `suspend fun markRefundReceived(transactionId: Long, atMillis: Long)` —
`UPDATE transactions SET refundedAt = :atMillis WHERE id = :id`.

### Step 2 — Budget credit on completion

When a refund is marked received, the app must credit the budget the same way
a manual `+` adjustment does (established semantics in
`ProcessIncomingSmsUseCase`: negative amount + `isAdjustment = true`). In
`BudgetTransactionHandler` add `suspend fun creditRefund(transaction: Transaction)`:
inserts a new row `amount = -original.amount`, `comment = "Refund: <original
comment>"` (string resource `refund_comment_prefix`), `isAdjustment = true`,
`refundedAt` NOT set on the new row (it tracks the *original*). Then set
`refundedAt` on the original row. If the user deletes the credit row, the
original can be re-marked (its `refundedAt` stays set — document this in the
KDoc: refund completion is idempotent per original row).

### Step 3 — Pending refunds card in History

Reuse the section pattern of `FutureRecurrentSection`
(`presentation/ui/history/sections/`): a `PendingRefundsSection` composable
shown only when the list is non-empty: row = comment, amount, days pending
(`refundedAt == null && refundExpected`), a **Received** button calling the
VM. VM: `val pendingRefunds: StateFlow<List<Transaction>>` +
`fun onRefundReceived(transaction)` (calls Step 2, snackbars on failure —
mirror `markTransactionAsPaid` in `HistoryViewModel.kt:248-260`).

### Step 4 — Refund SMS link (small, self-contained)

When `ProcessIncomingSmsUseCase` captures a **credit** SMS whose body contains
a refund cue (`refund|refunded|reversed|cashback` — add `REFUND_CUE` regex to
`BankSmsParser`), insert the credit row with
`comment = "Refund: " + (merchant ?: sender)` — so users see refund-credits
consistently. (Full matching of refund → original expense needs merchant
matching and is out of scope; the credit row is what the budget needs.)

### Step 5 — Weekly nudge (optional, opt-in string toggle in Settings)

`RefundNudgeWorker` following `WeeklyDigestWorker` exactly (entry-point DI
pattern, WORK_NAME, `PeriodicWorkRequestBuilder` 7 days): posts
`NotificationHelper.showRefundNudge(pendingCount)` only when
`observePendingRefunds().first().isNotEmpty()`. Settings toggle follows
`onWeeklyDigestToggle` in `SettingsViewModel.kt:212-217`.

Strings: `refund_section_title`, `refund_received`, `refund_pending_days`
("Pending %1$d days"), `refund_comment_prefix`, `refund_nudge_title`,
`refund_nudge_body`, settings toggle labels (all + es/fr).

## Out of scope

- Matching refund credits to specific original expenses automatically.
- Analytics changes (pending total appears in History only).
- Wear module.

## Test plan

- `HistoryViewModelTest`: `onRefundReceived inserts credit row and marks original` —
  mock the handler + repository, assert both calls and snackbar on failure.
- `ProcessIncomingSmsUseCaseTest`: refund-cue credit SMS gets the
  `Refund:` comment prefix.

## Done criteria

1. `./gradlew :app:compileFossDebugKotlin` — exit 0.
2. `./gradlew :app:testFossDebugUnitTest` — exit 0 including the new tests.
3. `grep -n "observePendingRefunds" app/src/main/java/com/sachit/moneypal/data/local/dao/TransactionDao.kt` — 1 match.
4. `grep -n "refund_" app/src/main/res/values/strings.xml` — ≥ 7 matches.

## Maintenance notes

- No schema change: `refundExpected`/`refundedAt` have existed since schema 19
  — do NOT add columns.
- The nudge worker must follow the digest worker's disabled-by-default
  contract (plan 003) — never schedule on install.
