# Plan 031 — Spending calendar heatmap (month grid in History)

**Status:** TODO
**Written against commit:** `467ce9d` (2026-09-19) — round 5 (features)
**Category:** feature (medium)
**Depends on:** nothing
**Effort:** M · **Risk of fix:** low

## Why

History is a list; users cannot see *shape* — which days spike, which weeks
run hot. A GitHub-style month-grid heatmap (color intensity = day spend) gives
an at-a-glance answer to "when do I overspend?" and doubles as a navigation
device: tapping a day scrolls History to that date. All the data exists; this
is a presentation win powered by one small pure calculator.

## Conventions (every round-5 plan)

- User-facing strings in `app/src/main/res/values/strings.xml` **plus
  `values-es` and `values-fr`** (other locales are Crowdin-managed).
- Money = `BigDecimal`/plain strings, never `Double`.
- Pure logic in `domain/` with JUnit4 + Truth tests (pattern:
  `NoSpendStreakCalculatorTest.kt`).
- Conventional commits, one commit for this plan.
- Global verification gate:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

## Current state (verified)

- `TransactionDao.getTransactionsForDateRange(startDate, endDate)` (lines
  ~25-30) returns a `Flow<List<TransactionEntity>>` — the month window can be
  loaded without any new query.
- `HistoryViewModel` already combines transactions + filters
  (`HistoryViewModel.kt` line ~452 `filterTransactions(...)`); the heatmap
  should hang off the same flow, not open its own DB connection.
- Exemplars for custom Compose drawing exist:
  `presentation/ui/theme/component/charts/TrendingGraph.kt` and
  `DetailedChart.kt` (Canvas-based, theme-aware). A calendar grid is just
  rows/columns of `Canvas` cells or a `Row`-of-`Column` layout — no new
  library.
- Budget context (what "high" means) is derivable from the existing budget
  state the History screen already receives (see
  `HistoryMviContract.kt` state fields — read the whole contract file before
  wiring).

## Steps

### Step 1 — Domain: day-spend aggregation + intensity buckets

New `app/src/main/java/com/sachit/moneypal/domain/calculator/SpendHeatmapCalculator.kt`:

- Input: transactions for a month window (domain `Transaction` list), the
  month (`YearMonth`), today (for "no future day" masking), and an optional
  daily reference (e.g. average day spend of the period or the period's
  daily budget — pick whichever the caller has; document choice).
- Output: `SpendHeatmap(days: List<HeatmapDay>)` where `HeatmapDay(date,
  totalSpend: BigDecimal, intensity: Intensity)` and `Intensity` is a
  5-level enum computed as spend ÷ reference (0 → NONE, ≤0.5 → LOW, ≤1 →
  MEDIUM, ≤1.5 → HIGH, else VERY_HIGH). Days with no transactions are NONE
  (not zero-spent — visually distinct); days after today are masked.
- Income rows excluded; recurring *templates* excluded but materialized
  occurrences included (mirror the semantics used by
  `NoSpendStreakCalculator` — read its KDoc and match).

### Step 2 — History MVI plumbing

- `HistoryMviContract`: add `heatmap: SpendHeatmap? = null` to the UiState and
  a `HeatmapDaySelected(val date: LocalDate)` intent.
- `HistoryViewModel`: compute the heatmap for the currently displayed period
  month from the transactions it already loads (no new DAO call needed unless
  the current flow is period-scoped — if the heatmap should span the whole
  month but History only loads the period, add a `getTransactionsForDateRange`
  subscription for the month window; report which path you took).

### Step 3 — Composable

New `presentation/ui/history/sections/SpendHeatmapSection.kt`:

- Month grid: weekday header row, weeks as rows; each day cell colored by
  intensity using the theme (extend `Color.kt`'s palette with 5 semantic
  heat tones that work in light+dark — follow the existing scheme file style,
  e.g. `schemes/Green.kt` naming).
- Tap → dispatch `HeatmapDaySelected`; History list scrolls/anchors to that
  date (the list is grouped by date — see `TransactionDateSections.kt` for
  the grouping keys; if list items aren't keyed by date, use
  `LazyListState.animateScrollToItem` with an index map; if indexing is
  infeasible, tapping may just apply a date filter — report the choice).
- Month navigation arrows above the grid (previous/next within periods that
  have data).
- Collapsible (start expanded=false) so it doesn't push the transaction list
  down by default; persist expansion in `HistoryFilterState`-like local
  state only (not DataStore).

## Out of scope

- Year-view heatmap, week-start customization (use `Locale` default).
- Widget or watch surfaces.
- Editing transactions from the heatmap.

## Test plan

- `SpendHeatmapCalculatorTest.kt` (JUnit4 + Truth): bucket boundaries exactly
  at 0.5/1/1.5 of reference; no-reference fallback (buckets by quantiles of
  the month's own spend — specify in KDoc); income excluded; template
  excluded; future days masked; empty month.
- Composable screenshot test if Paparazzi-friendly (pattern:
  `app/src/test/java/com/sachit/moneypal/presentation/ui/screenshot/`) —
  optional but encouraged; keep tolerance per `gradle.properties` paparazzi
  settings.

## Done criteria (machine-checkable)

1. `./gradlew :app:testFossDebugUnitTest` — exit 0 including
   `SpendHeatmapCalculatorTest`.
2. Compile gate — exit 0.
3. Manual: open History for a monthly budget with ≥2 weeks of data → heatmap
   renders, tapping a day with expenses anchors/filters the list to that date;
   no crash for empty months.

## Maintenance notes

- Keep the intensity enum in domain so Analytics could later reuse the same
  buckets (consistency across screens beats per-screen palettes).
- If a later plan adds localization for week start, do it here once — not in
  the composable.

## Escape hatches

- If History's transaction flow cannot cover the month window without a
  second subscription that would noticeably regress list performance, STOP
  and report the perf numbers — the heatmap may need to live in Analytics
  instead (product call, not a coding decision).
- If date-grouped list anchoring is structurally impossible, deliver the
  date-filter fallback and say so in the commit body.
