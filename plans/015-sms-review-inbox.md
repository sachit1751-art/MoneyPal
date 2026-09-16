# Plan 015 — Low-confidence SMS review inbox

**Status:** TODO
**Written against commit:** `ff6f773` (2026-09-16)
**Category:** feature
**Depends on:** 014 (source/captureConfidence columns, confidence scorer)
**Effort:** M · **Risk:** low (read-only list + existing edit/delete paths)

## Why

Plan 014 records `captureConfidence` but every capture still lands in the
budget silently. Rows below the confidence threshold (wrong merchant, wrong
amount picked from balance noise, credit misread) distort the budget until the
user happens to notice in History. A small "Review captures" inbox — entries
below `SmsCaptureConfidence.REVIEW_THRESHOLD` — makes the automation
trustworthy: confirm, fix, or dismiss with one tap each.

## Current state (verified)

- `HistoryViewModel` (`app/src/main/java/com/sachit/moneypal/presentation/ui/history/HistoryViewModel.kt`)
  already exposes transaction edit/delete via `budgetTransactionHandler` and
  shows snackbars on failure — the inbox reuses these exact paths.
- MVI style reference: the history screen contract lives in the same package
  (`HistoryUiState`, `HistoryUiEffect`, intents). Follow it.
- DAO: `TransactionDao` has queries filtered by boolean columns (e.g.
  `isCreditPaid`); a new `source IS 'sms' AND captureConfidence < :threshold
  AND NOT isDeleted` query follows the same style.

## Steps

### Step 1 — DAO + repository

`TransactionDao`: add

```kotlin
@Query("SELECT * FROM transactions WHERE source = 'sms' AND captureConfidence IS NOT NULL " +
       "AND captureConfidence < :threshold AND isDeleted = 0 ORDER BY createdAt DESC LIMIT 50")
fun observeSmsReviews(threshold: Int): Flow<List<TransactionEntity>>
```

`BudgetRepository`/`BudgetRepositoryImpl`: `fun observeSmsReviewCandidates(): Flow<List<Transaction>>`
hardcoding `SmsCaptureConfidence.REVIEW_THRESHOLD` (keep the constant as the
single source of truth).

### Step 2 — Entry point in History screen

Follow how the recurrent section is exposed: add a "Review N captures" chip
(only rendered when `N > 0`) in `CurrentPeriodRecurrentSection`'s parent
(`presentation/ui/history/sections/` — check where `FutureRecurrentSection` is
hosted and mirror the pattern) that opens a `ReviewSmsDialog` (Material3
dialog, NOT a new screen — keeps navigation untouched).

### Step 3 — The dialog (new file `presentation/ui/history/dialogs/SmsReviewDialog.kt`)

Each row: merchant comment, amount + direction, date, confidence badge, and
three actions — **Confirm** (set `captureConfidence = 100`, keep row), **Edit**
(open the existing `TransactionEditDialog` for the row), **Delete** (existing
soft-delete path). All three operate row-by-row via the existing
`budgetTransactionHandler` calls already used by `HistoryViewModel`.

Strings (values/strings.xml + es/fr copies, prefix `sms_review_`):
title, empty text, confirm, edit, delete, confidence label
("Confidence %1$d%%"), chip label "Review %1$d captures".

### Step 4 — ViewModel wiring

In `HistoryViewModel`: `val smsReviewCount: StateFlow<Int>` (map the repository
flow to `.size`, `stateIn(viewModelScope, WhileSubscribed(5000), 0)`), and
intents `ConfirmSmsCapture(transaction)`, plus dialog-visible state. Reuse
`TransactionActions`/handlers — do NOT add new repository mutations beyond the
confidence update (add `suspend fun confirmSmsCapture(id: Long)` on the
repository: `UPDATE transactions SET captureConfidence = 100 WHERE id = :id`).

## Out of scope

- Watch module, widgets, analytics integration.
- Editing confidence for manual rows (they have `source != "sms"`).

## Test plan

- DAO query logic is covered by the existing Room test pattern
  (`app/src/androidTest` has schema tests) — instead, unit-test the ViewModel:
  extend the history VM test following
  `app/src/test/java/com/sachit/moneypal/presentation/ui/history/HistoryViewModelTest.kt`:
  - `reviewCount exposes size of low-confidence rows`,
  - `confirm marks confidence 100 and removes from count`.
- `HistoryCalculationsTest` untouched.

## Done criteria

1. `./gradlew :app:compileFossDebugKotlin` — exit 0.
2. `./gradlew :app:testFossDebugUnitTest` — exit 0 including the 2 new tests.
3. `grep -n "confirmSmsCapture" app/src/main/java/com/sachit/moneypal/data/repository/BudgetRepository.kt` — 1 match.
4. `grep -rn "sms_review_" app/src/main/res/values/strings.xml` — ≥ 5 matches.

## Maintenance notes

- The LIMIT 50 keeps the dialog light; if users hit the cap regularly, the
  inbox deserves a real screen — revisit then, not now.
- If a future plan renames `source`, keep `'sms'` constant in ONE place
  (companion on `Transaction`) and reference it from DAO query strings via
  KSP-safe constants — Room queries can't interpolate Kotlin constants, so
  add a unit test asserting the literal matches the constant.
