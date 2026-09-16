# Plan 018 — Analytics: burn-rate forecast card

**Status:** TODO
**Written against commit:** `ff6f773` (2026-09-16)
**Category:** feature
**Depends on:** nothing
**Effort:** S · **Risk:** low (read-only surface over existing data)

## Why

`BudgetState` already computes `pacePercent` ("spending pace relative to
elapsed time, 100 = on pace") and `projectedOverspend` / 
`projectedExhaustionDate` (see `BudgetState.kt:31-40`,
`BurnRateCalculator`) — but they are **not surfaced anywhere in Analytics**.
Users only see pace reactively at period end via notifications. A compact
forecast card turns the math the app already does into a daily decision aid:
"At this pace you'll overshoot by ₹1,850 by the 24th."

## Current state (verified)

- `BudgetState.pacePercent: Int`, `projectedOverspend: BigDecimal`,
  `projectedExhaustionDate: LocalDate?` — computed in
  `domain/calculator/BurnRateCalculator.kt`, populated by
  `BudgetStateCalculator` (`BudgetStateCalculator.kt:205-214` region).
- `AnalyticsViewModel` (`presentation/ui/analytics/AnalyticsViewModel.kt`,
  683 lines) already collects `budgetState` for the selected/current period
  and exposes `uiState` with currency + period info; `AnalyticsScreen.kt`
  hosts cards — follow the existing card composable pattern there (search
  `Card(` usages and mirror one).

## Steps

### Step 1 — Card composable

New `presentation/ui/analytics/sections/BurnRateForecastCard.kt` (match the
package layout of existing analytics sections — check
`presentation/ui/analytics/` and its `util/` for helpers):

- **On pace** (pace ≤ 100): neutral card, "On pace" + "Projected: <remaining>
  by period end".
- **Over pace** (pace > 100): warning colors, "Overshooting by
  <projectedOverspend> by <projectedExhaustionDate>" (date formatted
  `DateTimeFormatter.ofPattern("d MMM")`, locale-aware).
- **Data missing** (`projectedExhaustionDate == null` and pace == 0): hide the
  card entirely (fresh period, <1 day elapsed).

Strings (`forecast_` prefix, + es/fr): title, on-pace line, over-pace line
with two placeholders (amount, date), "at this pace" subtitle.

### Step 2 — ViewModel exposure

In `AnalyticsViewModel`'s UI state add:

```kotlin
val pacePercent: Int? = null,
val projectedOverspend: BigDecimal? = null,
val projectedExhaustionDate: LocalDate? = null,
```

populated from the same `budgetState` flow it already collects — no new
repository calls. When `selectedPeriodId` shows a *past* period, pass nulls
(forecast is meaningless for closed periods).

### Step 3 — Tap-through

Tapping the card navigates to the main screen (existing
`AnalyticsUiEffect.NavigateToMain` effect — reuse it; budget adjustments live
there). No new effect type unless the codebase's effect naming demands it —
check `AnalyticsUiEffect` first and follow it.

## Out of scope

- Charts (the card is text-first; the analytics screen already has graphs).
- New calculations — this is pure surfacing of `BurnRateCalculator` output.
- Widgets / watch.

## Test plan

- Extract a tiny pure helper `BurnRateForecastUiModel.from(state, today):
  BurnRateForecastUiModel?` in `presentation/ui/analytics/util/` (nullable =
  hide card) and unit-test: on-pace mapping, over-pace mapping, hidden-when-
  fresh, hidden-for-past-period. Follow `AnalyticsViewModelTest` style for the
  VM nulls-for-past-period assertion if a test file exists
  (`app/src/test/.../analytics/AnalyticsViewModelTest.kt` does — extend it).

## Done criteria

1. `./gradlew :app:compileFossDebugKotlin` — exit 0.
2. `./gradlew :app:testFossDebugUnitTest` — exit 0 including the helper tests (≥3).
3. `grep -rn "pacePercent" app/src/main/java/com/sachit/moneypal/presentation/ui/analytics/` — ≥ 2 matches.

## Maintenance notes

- If `BurnRateCalculator` semantics change (pace definition), the card
  inherits them automatically — that is the point; do not duplicate math in
  the UI layer.
- Keep the hide-when-fresh rule strict: a "no data" card on day 1 reads as a
  bug to users.
