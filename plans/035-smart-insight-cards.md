# Plan 035 — Smart insight cards (spending anomaly detection in Analytics)

**Status:** TODO
**Written against commit:** `467ce9d` (2026-09-19) — round 5 (features)
**Category:** feature (medium)
**Depends on:** nothing (benefits from plan 027 landing first for exact totals)
**Effort:** M · **Risk of fix:** low

## Why

Analytics shows aggregates; it never says anything. Users get graphs but no
sentences. A small set of generated insight cards — "Food is 3× its usual
weekly level", "You've spent 85% of your budget with 10 days to go",
"Transport is down 40% vs last period" — turns existing computation into
guidance. This is the cheapest differentiator in the slate: pure domain
logic + a card list, no new screens, no DB changes.

## Conventions (every round-5 plan)

- User-facing strings in `app/src/main/res/values/strings.xml` **plus
  `values-es` and `values-fr`** (other locales are Crowdin-managed). Insight
  sentences need parameterized strings — use positional `%1$s`/`%2$d`
  placeholders consistently with existing plurals/params (see
  `no_spend_streak_title`).
- Money = `BigDecimal`/plain strings; percentages computed in domain as
  `BigDecimal`, formatted at the UI edge.
- Pure logic in `domain/` with JUnit4 + Truth tests.
- Conventional commits, one commit for this plan.
- Global verification gate:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

## Current state (verified)

- Analytics state already carries: `previousPeriodTransactions` (found via
  `AnalyticsViewModel.findPreviousPeriodTransactions`, line ~475),
  `envelopeProgress` (line ~441), `noSpendStreak` (line ~437),
  `savingsGoalProgress` (line ~445). The insight engine consumes exactly
  these — no new queries.
- Card UI pattern: `Analytics.kt` `NoSpendStreakCard` (~line 1104) and
  `EnvelopeProgressCard` (~line 853) — mirror visuals/spacing.
- Insight-type precedent for persisted one-time surfaces: none needed here —
  insights are derived, stateless, and re-render on data change (no
  dismissal persistence; they should change as data changes).

## Steps

### Step 1 — Domain insight engine

New `app/src/main/java/com/sachit/moneypal/domain/calculator/InsightEngine.kt`:

- Input data class `InsightContext(currentPeriodTransactions,
  previousPeriodTransactions, categories, envelopeProgress, daysInPeriod,
  daysElapsed, noSpendStreak, savingsGoalProgress)`.
- Rules (each returns `Insight(id, severity: POSITIVE|NEUTRAL|WARNING, args)`):
  1. Category anomaly: current-period category spend vs its trailing
     average (previous 2 periods' per-category spend, computed from
     `previousPeriodTransactions`' two most recent windows available — see
     note; if only one previous period's data exists, compare against it and
     say "vs last period"). Trigger at ≥2× and ≥ a minimum absolute
     threshold (avoid flagging ₹40 vs ₹35).
  2. Budget pace: projected end-of-period spend = spend ÷ daysElapsed ×
     daysInPeriod; trigger when projection ÷ budget ≥ 1.1.
  3. Envelope proximity: category ≥80% of its limit (complements the
     notification system with an in-app surface).
  4. Improvement: category spend ≤0.6× previous (positive framing).
  5. No-spend momentum: current streak ≥3 days (positive).
- Cap output at 4 insights, ordered WARNING → POSITIVE, then by magnitude.
  Never invent categories, never emit insights for categories with <3
  transactions (noise floor). All math BigDecimal; ratios as
  `BigDecimal` scaled ×100 rounded HALF_UP.

### Step 2 — MVI + UI

- Extend `AnalyticsMviContract`/`AnalyticsViewModel` (compute inside the
  existing combine(), from state already present — line ~434-445 region).
- New `InsightCardsSection` in `Analytics.kt`, placed above the existing
  charts; each card maps severity → color role (error/tertiary/primary
  container) and uses two parameterized strings per insight type
  (`insight_anomaly_*`, `insight_pace_*`, `insight_envelope_*`,
  `insight_improvement_*`, `insight_streak_*`) in all three locales. Cards
  without data simply don't render; an empty insight list renders nothing
  (no placeholder card).

### Step 3 — Determinism guard

Insights must be a pure function of state (no clock reads inside the engine —
`daysElapsed` comes in as input). This keeps them Paparazzi-testable and
replayable.

## Out of scope

- Machine learning / on-device training — rule-based only.
- Dismissal persistence (insights reflect data; they disappear when no longer
  true).
- Push notifications for insights (envelope alerts already notify; this is
  the in-app surface).
- Wear module.

## Test plan

- `InsightEngineTest.kt`: anomaly triggers at 2× with minimum-absolute floor
  respected; no trigger with <3 transactions; pace projection math
  (including daysElapsed=0 → no pace insight); envelope 80% boundary
  (79.99 no, 80 yes); improvement 0.6 boundary; streak ≥3; cap-at-4 and
  ordering; empty context → empty list; determinism (same input → same
  output, run twice).

## Done criteria (machine-checkable)

1. `./gradlew :app:testFossDebugUnitTest` — exit 0 including
   `InsightEngineTest`.
2. Compile gate — exit 0.
3. `grep -c "insight_" app/src/main/res/values/strings.xml
   app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml`
   — ≥8 matches in each (2+ strings × 5 rule families minus unused).
4. Manual: seed a period where food spend doubles vs prior data → warning
   card renders with correct numbers; a clean period shows only positive/no
   cards.

## Maintenance notes

- New rules are additive: one rule = one pure function + strings + tests.
  Resist cross-rule state (each rule sees only `InsightContext`).
- The ≥2×/±0.6/80% thresholds are starting points; if the maintainer wants
  tunability later, hoist them to a single `InsightThresholds` data class in
  the engine's constructor with defaults.

## Escape hatches

- If previous-period category data cannot be derived from
  `previousPeriodTransactions` (e.g. it's period-scoped, not
  category-complete), scope rules 1 and 4 to compare against the trailing
  90 days loaded via `getTransactionsForDateRange` — report the deviation.
- If the insight list makes Analytics' first frame janky (measure with a
  synthetic 1k-transaction period), compute insights in `Flow.flowOn`
  default dispatcher context — do not block the main thread.
