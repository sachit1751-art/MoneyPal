# Plan 046 — Income vs spend analytics

**Status:** DONE — `e68c1ce`
**Written against commit:** `48bb35b` (2026-09-23)
**Category:** feature (medium)
**Depends on:** plan 027 (landed)
**Effort:** M · **Risk:** low

## Why

Income tracking exists (plan 006) and Analytics already renders
`state.incomes` (Analytics.kt lines 132/863/1018), but the analytics page has
no *comparison* view: spend vs income for the period, savings rate, and
trend vs the previous period. Users recording income currently get numbers
with no insight.

## Current state (verified)

- `Transaction.isIncome` marks money-in rows; income rows are excluded from
  spend math everywhere (documented on the field).
- Analytics section list is order-driven (Analytics.kt ~lines 215–231);
  sections: Heatmap(3), MinMax(0), Credit Owed(6), Categories(4), Savings(5).
- DAO returns raw amounts (BigDecimal-in-Kotlin pattern from plan 027); no
  SQL aggregation.
- Existing charts are Compose-drawn; `state.incomes` is already a
  `List<Transaction>`.

## Steps

1. **Domain**: `IncomeSpendComparisonCalculator` — pure: given current +
   previous period transactions, produce
   `(totalIncome, totalSpend, net, savingsRatePct, incomeDeltaPctVsPrev,
   spendDeltaPctVsPrev)`; `BigDecimal` throughout; percentages as scaled
   BigDecimal with explicit rounding mode; guard divide-by-zero (previous
   period zero income → delta null, UI shows "—"). Truth tests.
2. **DAO**: two projections (current + previous period ranges) reusing the
   existing raw-amount queries with a date-range parameter — verify whether
   the existing queries take ranges already (lines 42/51 suggest yes).
3. **ViewModel/UI**: new "Income vs spend" card after Savings (index 5),
   collapsible, with two progress bars (spend/income), net badge
   (green/red), and delta arrows vs previous period. Follow the existing
   section index pattern; strings ×3 locales.
4. **Tutorial hint**: Analytics has per-section tutorial entries (see
   `analytics_tutorial_heatmap_*`) — add matching strings for the new card.

## Out of scope

- Multi-month trend chart (nice-to-have follow-up), wear, income budgets.

## Test plan

- `IncomeSpendComparisonCalculatorTest`: zero-previous guards, savings rate
  rounding, income-only period, spend-only period.
- ViewModel test: card state reflects calculator output; hidden when both
  totals are zero.

## Done criteria

1. New card renders between Savings and Credit Owed (section index inserted).
2. Verification gate exit 0 including calculator tests.

## Escape hatches

- If the previous-period query forces N+1 reads, fetch once into memory and
  filter in Kotlin (repo pattern) rather than adding a correlated SQL query.
