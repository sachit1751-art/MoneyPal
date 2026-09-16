# Plan 019 — Monthly report share (text)

**Status:** TODO
**Written against commit:** `ff6f773` (2026-09-16)
**Category:** feature
**Depends on:** nothing
**Effort:** S · **Risk:** low

## Why

`domain/report/SpendingReportBuilder.kt` exists but its output has no share
surface — users who want to send "what we spent in August" (partner, expense
reimbursement, records) must read numbers off the screen. A one-tap
Android share sheet (plain text, no file I/O, no permissions) completes the
feature loop using the existing builder.

## Current state (verified)

- `domain/report/SpendingReportBuilder.kt` — pure builder in `domain/report/`.
  (Read it before wiring; if it lacks a period parameter, add one rather than
  constructing it twice.)
- `AnalyticsViewModel` already holds the selected period's transactions and
  budget settings for the analytics screen — the report is derivable there
  with no new repository calls.
- The codebase's existing share/intent patterns: CSV export writes via SAF
  (`presentation/ui/settings/csv/`) — deliberately NOT reused here (text share
  avoids storage permissions entirely).

## Steps

### Step 1 — Report text formatting

Add to `SpendingReportBuilder` (or a thin wrapper next to it, if the builder
returns a model rather than text):

```kotlin
/** Human-readable plain-text report for the share sheet (plan 019). */
fun buildTextReport(
    transactions: List<Transaction>,
    settings: BudgetSettings,
    periodStart: LocalDate,
    periodEnd: LocalDate,
): String
```

Format (plain text, emoji-free, locale-currency formatted via
`symbolOnlyCurrencyFormat` — see its use in `BudgetViewModel.kt:486`):

```
MoneyPal report: 1 Aug – 31 Aug 2026
Budget: Rs 30,000   Spent: Rs 22,450 (75%)
Top categories:
1. Groceries — Rs 8,200
2. Transport — Rs 5,100
3. Eating out — Rs 4,050
Transactions: 47
```

Top 5 categories by spend (reuse the category-attribution filter semantics of
`EnvelopeCalculator`: positive, non-adjustment, non-income, non-deleted).

### Step 2 — UI hook

In the analytics screen's period header (find the period selector row in
`AnalyticsScreen.kt`), add an overflow/share icon action (Material icons are
already a dependency — `material-icons-extended`). On tap:

```kotlin
val sendIntent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_SUBJECT, subject)
    putExtra(Intent.EXTRA_TEXT, reportText)
}
context.startActivity(Intent.createChooser(sendIntent, null))
```

Wire through the MVI contract (`AnalyticsUiEffect.ShareReport(subject, text)`)
— the effect collector in the activity/composable host performs the actual
`startActivity` (UI-layer concern), keeping the VM Android-free except for the
effect type.

### Step 3 — Strings

`report_share_subject` ("MoneyPal report %1$s"), icon content-description
`report_share_cd`. (+ es/fr.)

## Out of scope

- PDF/HTML report, file export (CSV already covers files), watch.

## Test plan

- `SpendingReportBuilderTest` (extend or create in `domain/report`):
  - totals and percentage math;
  - top-5 ordering + exclusion of adjustments/income/deleted;
  - date-range formatting;
  - empty-period output (no crash, "Transactions: 0").

## Done criteria

1. `./gradlew :app:compileFossDebugKotlin` — exit 0.
2. `./gradlew :app:testFossDebugUnitTest` — exit 0 including report tests (≥4).
3. `grep -n "buildTextReport" app/src/main/java/com/sachit/moneypal/domain/report/SpendingReportBuilder.kt` — 1 match.

## Maintenance notes

- Keep the text format stable-ish: users may script around shared reports;
  changes are fine but should be additive lines, not renames.
- If a future plan adds PDF, it should consume the same builder model —
  do not fork the aggregation logic.
