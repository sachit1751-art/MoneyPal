# Plan 032 — Year in review: annual summary screen

**Status:** TODO
**Written against commit:** `467ce9d` (2026-09-19) — round 5 (features)
**Category:** feature (medium, delight)
**Depends on:** nothing hard; plan 027 (decimal totals in SQL) should land
first so any DAO-level totals it reuses are exact
**Effort:** M · **Risk of fix:** low

## Why

Every finance app ships a "wrapped"-style annual recap; MoneyPal has all the
raw data but no such moment. A Year in Review screen gives users the emotional
payoff of seeing their year (total spend, biggest expense, top category,
no-spend record, savings progress) and is highly shareable — organic
marketing. All aggregations already exist in domain calculators; the plan assembles
them for a 12-month window.

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

- Year of transactions: `TransactionDao.getTransactionsForDateRange()`
  (lines ~25-30) — one call with Jan 1 → Jan 1 next year bounds.
- Reusable domain calculators (reuse, do not duplicate):
  - `NoSpendStreakCalculator` (domain/calculator/) — no-spend days; pass a
    365-day window.
  - `SavingsGoalAggregator` / `SavingsGoalCalculator` — monthly saved totals
    + goal progress (AnalyticsViewModel lines ~459-472 show the call shape).
  - `EnvelopeCalculator` — per-category progress if limits exist.
- Screen exemplar with sections: `presentation/ui/analytics/Analytics.kt`
  (cards pattern, `SavingsGoalProgressSection` at ~line 1218).
- Navigation: Compose Navigation (see `MainActivity.kt` NavHost and existing
  routes for History/Analytics/Settings) — follow the existing route pattern
  exactly (read it; do not invent a parallel nav mechanism).

## Steps

### Step 1 — Domain: year summary builder

New `app/src/main/java/com/sachit/moneypal/domain/calculator/YearReviewCalculator.kt`:

- Input: transactions for the year, categories, optional savings progress,
  currency.
- Output `YearReview`: totalSpend, monthCount (months with ≥1 expense),
  averageMonthlySpend, topCategory (name + share), biggestExpense (amount +
  comment/date), noSpendDays count, incomeTotal, months where spend exceeded
  average (count), plus a per-month `List<MonthTotal>` (month, spend:
  BigDecimal) for a mini bar chart.
- All money as `BigDecimal`; shares as `BigDecimal` percent rounded to 2
  places, `HALF_UP`.

### Step 2 — ViewModel + screen

- New `presentation/ui/yeareview/YearReviewViewModel.kt` +
  `YearReviewMviContract.kt` (MVI per convention: state, intents, effects).
  Loads: year window via repository (add a thin suspend
  `getTransactionsForRange(from: LocalDate, to: LocalDate): List<Transaction>`
  to `BudgetRepository`/Impl mapping over the existing DAO Flow's first
  emission if no suspend variant exists — check first; prefer adding the
  suspend DAO twin `getTransactionsForDateRangeList` over Flow-first
  gymnastics), categories, settings.
- Screen `YearReviewScreen.kt`: scrollable card stack — headline total,
  monthly bar chart (reuse/extend the existing chart components under
  `presentation/ui/theme/component/charts/`), top-category card,
  biggest-expense card, no-spend card, savings card. Share button: render the
  same values into a plain-text share sheet (PDF is plan 039's job; keep this
  text-only to avoid overlap).
- Entry point: Analytics top bar (calendar/menu icon) or a card shown during
  December–January; pick ONE — Analytics entry is the cheaper, always-
  available route; document choice in commit body.

### Step 3 — Strings + polish

`year_review_*` strings, all three locales. Numbers formatted via existing
`NumberFormat.kt` formatters with the budget currency.

## Out of scope

- Image/story generation, PDF (039), animations beyond standard transitions.
- Comparing multiple years (single-year v1; the calculator accepts any
  window, so a year picker later is trivial).
- Wear module.

## Test plan

- `YearReviewCalculatorTest.kt`: empty year → zeroed report (no NaNs, shares
  0); single-month year; top-category tie-breaking (deterministic: earliest
  name wins — encode in KDoc); biggest expense tie; income excluded from
  spend; months-with-spend count; per-month totals exclude income and
  templates.
- ViewModel test with mocked repository (pattern:
  `ChangelogHistoryViewModelTest.kt` — mockk + turbine).

## Done criteria (machine-checkable)

1. `./gradlew :app:testFossDebugUnitTest` — exit 0 including
   `YearReviewCalculatorTest`.
2. Compile gate — exit 0.
3. `grep -rn "YearReviewCalculator" app/src/main --include="*.kt"` — domain +
   ViewModel references.
4. Manual: with a full year of test data, all cards render real numbers;
   share sheet emits a readable text summary; empty-data year shows a
   graceful empty state.

## Maintenance notes

- `YearReview` model is window-agnostic in practice; if the maintainer later
  wants "last 90 days" or quarter recaps, parameterize rather than fork.
- Keep the calculator free of Android imports so the widget layer could reuse
  it.

## Escape hatches

- If the year of transactions is too large to load in one query on low-RAM
  devices (test with a synthetic 5k-row DB), switch to per-month DAO calls
  and aggregate incrementally — report the pivot in the commit body.
- If `BudgetRepository` already exposes a suitable suspend range query, use
  it instead of adding a new one and note the reuse.
