# Plan 012 — Savings goals (tracked, with progress)

Written against commit **`614cb49`** (`git rev-parse --short HEAD` should print
`614cb49`). If HEAD differs, re-verify excerpts before executing.

## Why this matters

`SavingsPreferences` already models a goal (`savingsGoalAmount` +
`savingsGoalMonths`) and the Analytics card projects "save X per period to
reach Y in N months" (see the KDoc in
`app/src/main/java/com/sachit/moneypal/domain/model/SavingsPreferences.kt` and
`projectedPerPeriod()`). But the goal is **static text** — nothing tracks
progress toward it. Promoting it to a tracked goal (per-period saved amount vs.
target, cumulative progress bar, finish-date estimate) is a small-to-medium
change that turns an existing projection into a motivating feature, reusing the
existing preferences storage with no DB migration.

## Current state (verified excerpts)

`SavingsPreferences.kt` (domain model, fully verified):

```kotlin
data class SavingsPreferences(
    val preset: SavingsSplitPreset = SavingsSplitPreset.BALANCED,
    val needsPct: Int = preset.needsPct,
    val wantsPct: Int = preset.wantsPct,
    val savingsPct: Int = preset.savingsPct,
    val savingsGoalAmount: BigDecimal? = null,
    val savingsGoalMonths: Int? = null,
) {
    fun projectedPerPeriod(): BigDecimal? { ... }   // goal / months, 2dp HALF_UP
}
```

- Persisted via `SettingsRepository.setSavingsPreferences(prefs)` →
  `SettingsRepositoryImpl.kt` line ~487 (DataStore-backed; there are round-trip
  tests in `app/src/test/java/com/sachit/moneypal/data/repository/SettingsRepositoryImplTest.kt`
  lines ~177–196 to extend as the pattern).
- The editor UI is `presentation/ui/settings/savings/SavingsPreferencesEditor.kt`
  (used from `Settings.kt` line ~732), and the card that renders the projection
  lives under `presentation/ui/theme/component/budget/` (savings recommendation
  card — locate by searching `savingsPreferences` usages in `analytics/`).
- "Saved" is derivable: income transactions are negative amounts
  (`BudgetStateCalculator` sums `amount < BigDecimal.ZERO` as income), and the
  app has no explicit savings-account concept — v1 defines "saved this period"
  as **the unspent remainder of the period's savings allocation**, i.e.
  `savingsPct% of effectiveTotalBudget − (income added by user is separate)`.
  Simpler and honest definition for v1: **saved = income transactions
  (negative amounts) recorded in the period, PLUS unspent daily allocation
  carryover is NOT counted** (too speculative). Document this definition in
  the card's caption string so users know what is measured.

## Design decisions

- **No DB migration.** Goal progress is computed on the fly from
  `SavingsPreferences` + period income + elapsed periods since the goal was
  set. Add `savingsGoalStartedAtEpochDay: Long? = null` to
  `SavingsPreferences` (DataStore persists it with the existing
  `setSavingsPreferences` path — extend its JSON/typed encoding there; check
  how the impl serializes `SavingsPreferences` — if it stores individual keys,
  add one nullable-long key; if JSON, the field rides along).
- **Progress model (pure function, unit-tested):**
  - `periodsElapsed = months between goalStart and now` (fractional → floor,
    min 0), where a "period" for the goal is always a calendar month
    regardless of budget period (matches "reach in N months").
  - `savedSoFar` = Σ income transactions (negative amounts, non-deleted,
    non-recurrent, non-adjustment) dated since `goalStartedAt`.
  - `targetByNow = projectedPerPeriod() × periodsElapsed`.
  - `onTrack = savedSoFar >= targetByNow × 0.9` (10% grace to avoid nagging).
  - `etaMonths = ceil((goal − savedSoFar) / averageMonthlySaved)` when
    savedSoFar > 0, else null.
- **Where it renders:** extend the existing savings card on Analytics with a
  determinate progress indicator and the ETA line. The card is shared with
  preview fixtures — update `AnalyticsPreviewHelpers.kt` accordingly.
- Keep `SavingsPreferencesEditor` as the single editing surface; add the "set
  goal" flow there if the amount/months fields are not already editable (they
  are, per the KDoc: "to reach $X in N months, save $Y per period").

## In scope

- `app/src/main/java/com/sachit/moneypal/domain/model/SavingsPreferences.kt` (add `savingsGoalStartedAtEpochDay`)
- `app/src/main/java/com/sachit/moneypal/data/repository/SettingsRepositoryImpl.kt` (persist new field)
- New: `app/src/main/java/com/sachit/moneypal/domain/calculator/SavingsGoalCalculator.kt`
- `app/src/main/java/com/sachit/moneypal/presentation/ui/analytics/AnalyticsViewModel.kt` (feed goal inputs)
- The savings card composable (locate via `SavingsPreferences` usages under `presentation/ui/`)
- `app/src/main/java/com/sachit/moneypal/presentation/ui/analytics/util/AnalyticsPreviewHelpers.kt`
- `app/src/main/res/values/strings.xml`
- Tests: `app/src/test/java/com/sachit/moneypal/domain/calculator/SavingsGoalCalculatorTest.kt`, extend `SettingsRepositoryImplTest`

## Out of scope

- Dedicated savings-account balances, transfer transactions, or a "move money
  to savings" action.
- Multiple simultaneous goals.
- Wear.

## Steps

1. **Model.** Add `val savingsGoalStartedAtEpochDay: Long? = null` to
   `SavingsPreferences`. When `setSavingsPreferences` persists a prefs object
   whose `savingsGoalAmount != null` and the started-at is null, stamp
   `LocalDate.now().toEpochDay()` **in the ViewModel/use-case layer**, not in
   the data class default (keeps the model pure and testable). Clearing the
   goal amount clears started-at.
2. **Persistence.** Open `SettingsRepositoryImpl.setSavingsPreferences`
   (line ~487) and the corresponding reader in `observeSettings`; follow
   however the existing goal fields are stored and add the one new
   nullable-long key (`savings_goal_started_at_epoch_day`). Extend
   `SettingsRepositoryImplTest` round-trip tests (lines ~177–196) with the
   new field.
3. **SavingsGoalCalculator** (pure, `@Inject` like `RecurringExpenseCalculator`):

   ```kotlin
   data class SavingsGoalStatus(
       val goalAmount: BigDecimal,
       val savedSoFar: BigDecimal,
       val targetByNow: BigDecimal,
       val onTrack: Boolean,
       val progressFraction: BigDecimal,  // savedSoFar/goal, 4dp, capped 1.0
       val etaMonths: Int?,               // null when not computable
       val perPeriodTarget: BigDecimal,   // from SavingsPreferences.projectedPerPeriod()
   )
   fun computeStatus(
       prefs: SavingsPreferences,
       incomeTransactions: List<Transaction>,  // full, unfiltered; calc filters
       today: LocalDate,
   ): SavingsGoalStatus?
   ```

   Returns null when `savingsGoalAmount == null || savingsGoalMonths == null`.
   Income = `amount < 0 && !isDeleted && !isRecurrent && !isAdjustment &&
   date != null && date.toLocalDate().toEpochDay() >= (startedAt ?: Long.MIN_VALUE)`.
   Months elapsed = `ChronoUnit.MONTHS.between(start, today)` floored, min 0.
4. **ViewModel.** In `AnalyticsViewModel`, the big `combine()` already exposes
   transactions; compute the status inside the existing reduce (do NOT add a
   flow to the combine — its array-shape combine is called out in source as
   crash-critical; derive from data already present, or add the status as a
   pure map over existing state). Attach to the display state.
5. **Card UI.** Add: progress bar (`LinearProgressIndicator` with
   `progressFraction`), "on track"/"behind" label, ETA line
   ("At this pace you'll reach your goal around %1$s" — month-year), and a
   caption defining what counts as saved (string
   `savings_goal_definition` — "Counted from income entries recorded since you
   set this goal."). Use theme colors only; behind-state uses the error color.
6. **Strings** (`values/strings.xml`): `savings_goal_on_track` ("On track"),
   `savings_goal_behind` ("Behind schedule"), `savings_goal_eta`
   ("At this pace you'll reach your goal around %1$s"),
   `savings_goal_progress_format` ("%1$s of %2$s saved"),
   `savings_goal_definition` (above).
7. **Previews.** Update `AnalyticsPreviewHelpers.kt` so Paparazzi fixtures
   compile; add one fixture with an active goal.
8. **Tests.** `SavingsGoalCalculatorTest`: null goal → null; income before
   goal start excluded; exactly-at-boundary date included; targetByNow at
   month boundaries; onTrack grace band; progressFraction capping; ETA
   rounding. Mirror `RecurringExpenseCalculatorTest` fixture style.

## Verification gates

```bash
./gradlew :app:compileFossDebugKotlin
./gradlew :app:testFossDebugUnitTest
```

## Done criteria

- [ ] Setting a goal in Settings shows live progress on the Analytics card within the same session.
- [ ] Progress math matches the unit-tested definition (income-since-start vs. per-period target).
- [ ] DataStore round-trips the new field (extended tests pass).
- [ ] Verification gates green.

## Maintenance notes

- If a future plan introduces a real savings-account concept, replace the
  income-based `savedSoFar` definition — the calculator is the single seam.
- Do not add goal fields to `BudgetSettings` or Room — DataStore-only by design.

## Escape hatches — STOP and report if

- `SettingsRepositoryImpl` serializes `SavingsPreferences` as a single opaque
  JSON blob without a schema the new field can ride in safely — report the
  actual mechanism found and propose the minimal extension.
- The savings card composable cannot be located by searching `SavingsPreferences`
  usages (meaning the projection is rendered somewhere unexpected) — report
  candidates before writing UI.
