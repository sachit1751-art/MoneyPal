# Plan 016 — Recurring payment merchant aliases (link ad-hoc spend to its subscription)

**Status:** TODO
**Written against commit:** `ff6f773` (2026-09-16)
**Category:** feature
**Depends on:** nothing (coordinate Room migration with 014 — see dependency rules)
**Effort:** M · **Risk:** low-medium (one nullable column + pure logic)

## Why

Users pay Netflix ad-hoc three months, *then* register it as recurring (or the
reverse). Today the app shows both the recurring template's next charge AND
the ad-hoc rows with no connection: upcoming-charges lists can double-count a
charge the user already paid ad-hoc, and analytics treats them as unrelated
spend streams. Linking ad-hoc rows to their recurring template by amount+date
proximity ("merchant alias") powers: "you already paid this cycle" suppression,
per-subscription total spend, and smarter suggestions.

## Current state (verified)

- `Transaction.sourceTransactionId: Long?` already exists in the domain model
  and entity (`TransactionEntity.sourceTransactionId` — added for recurring
  instances; virtual history rows set it, see
  `HistoryCalculations.kt:196-199`).
- `RecurringExpenseCalculator.nextOccurrenceDate(tx, today)` computes expected
  charge dates (plan 008).
- `CategorySuggester.buildIndex` already counts comment tokens per category —
  reusable for "this merchant is usually X".

## Steps

### Step 1 — Pure linker (domain)

New `domain/calculator/RecurringLinker.kt`:

```kotlin
/**
 * Links ad-hoc (non-recurrent) transactions to recurring templates by
 * amount + date proximity. Pure function, unit-tested.
 *
 * A transaction links to a template when ALL hold:
 *  - template isRecurrent && !isRecurrentPaused && !isDeleted
 *  - tx.amount == template.amount (exact BigDecimal compare)
 *  - tx date is within +-ALLOWED_DRIFT_DAYS (default 3) of the template's
 *    occurrence date for that cycle (via RecurringExpenseCalculator)
 *  - tx is not itself recurrent and has sourceTransactionId == null
 */
class RecurringLinker @Inject constructor(
    private val calculator: RecurringExpenseCalculator,
) {
    fun link(
        adHoc: List<Transaction>,
        templates: List<Transaction>,
        today: LocalDate,
    ): Map<Long, Long> // adHoc tx id -> template id
}
```

Implementation notes: iterate templates, compute the occurrence date window
around each cycle from `startDate` to `today` (monthly clamp: reuse
`monthlyOccurrenceDay`), match by amount first (hash), then date window.
O(adHoc × templates × cycles) is fine at personal-finance scale.

### Step 2 — Suppression in upcoming lists

`HistoryCalculations.buildUpcomingRecurrentItems` currently suppresses only by
`paidOccurrences`. Extend its callers (`HistoryViewModel` prepares the inputs)
to also suppress an upcoming item when a **linked ad-hoc transaction with a
date inside that cycle's window** exists. Implementation: compute the link map
once per emission in `HistoryViewModel` and pass a
`linkedTemplateIds: Set<Long>` (templates with ≥1 linked ad-hoc row in the
current cycle) into the existing builder — extend the builder signature with
an optional parameter defaulting to empty (all existing tests keep compiling).

### Step 3 — Per-subscription totals in History

In the recurrent section rows (`UpcomingRecurrentItemRow` / its hosting
section), add a subtitle "Paid X of Y this year" computed from the link map —
string `recurrent_paid_cycles` ("Paid %1$d cycles this year"). Keep it
text-only; no charts.

### Step 4 — Tests

- `RecurringLinkerTest` (new, `domain/calculator`):
  - exact amount + date inside window links;
  - amount differs → no link;
  - paused/deleted template → no link;
  - monthly clamp window (billing on the 31st, Feb 28) links;
  - already-linked tx (sourceTransactionId != null) ignored.

## Out of scope

- Any Room migration: linking is computed at read time; `sourceTransactionId`
  reuse means NO schema change (that is why this plan is M, not L).
- Write-back of links into the DB (follow-up if users want manual linking).
- Wear module.

## Done criteria

1. `./gradlew :app:compileFossDebugKotlin` — exit 0.
2. `./gradlew :app:testFossDebugUnitTest` — exit 0 including `RecurringLinkerTest` (≥5 tests).
3. `grep -n "linkedTemplateIds" app/src/main/java/com/sachit/moneypal/presentation/ui/history/HistoryCalculations.kt` — ≥ 1.

## Maintenance notes

- The exact-amount match is deliberately strict; loosening to a tolerance
  needs a user setting (price hikes would silently stop linking otherwise).
- If plan 014's `source = "sms"` rows capture subscription charges, they are
  ad-hoc rows and benefit from this linking automatically — no extra work.
