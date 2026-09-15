# Plan 002 — Tracked savings goals

## Summary
`SavingsPreferences` already has `savingsGoalAmount` + `savingsGoalMonths` and
`SavingsRecommendationCard` projects per-period savings. Promote the goal to
**tracked progress**: cumulative saved amount vs target, progress bar, and an
estimated finish date.

## Approach (no DB migration)
- The savings allocation per period is derived from period spending via the
  existing split preset (`savingsPct`). Compute "saved so far" as
  `sum(savingsPct-slice of each period's total) across periods since the goal
  was set` — a new pure calculator `SavingsGoalCalculator` in `domain/calculator/`
  (input: past periods' totals + start date + goal; output: saved, target,
  progress fraction, ETA date).
- Store `savingsGoalStartDate: Long?` (epoch millis) in DataStore via
  `SettingsRepository` when the user sets/edits a goal (no Room change).

## UI
- `SavingsRecommendationCard`: when a goal exists, render a determinate
  progress bar with "saved X of Y (Z%)" and "on track to finish ~<Month Year>"
  (ETA from calculator; "— " when spending makes ETA indeterminate).
- Settings savings editor (`SavingsPreferencesEditor`): setting an amount
  records the start date; clearing the goal clears it.

## Strings
- 3–4 new strings in `values/strings.xml` (+ `values-es`, `values-fr`).

## Tests
- `SavingsGoalCalculatorTest` (follow `NoSpendStreakCalculatorTest` pattern):
  zero progress, partial progress, over-achieved goal (clamps at 100%),
  ETA math incl. month-boundary, indeterminate case (no spending yet).

## Verification
- `./gradlew :app:testFossDebugUnitTest`
- Paparazzi: savings card goldens will change → refresh after verify run.
