# Plan 022 — Savings goal home-screen widget

**Status:** TODO
**Written against commit:** `cb5be35` (2026-09-16)
**Category:** feature
**Depends on:** nothing
**Effort:** M · **Risk:** low (mirrors an existing widget 1:1)

## Why

Savings goals are computed in `AnalyticsViewModel.computeSavingsGoalProgress`
(`savingsGoalAmount` from `SavingsPreferences`) and shown only inside
Analytics. A home-screen widget gives the goal ambient visibility, matching
the existing widget family (10 glance widgets already ship).

## Exemplar to mirror (verified)

`app/src/main/java/com/sachit/moneypal/presentation/widget/AverageSpendWidget.kt`:

- `class AverageSpendWidgetReceiver : GlanceAppWidgetReceiver()` (line 60),
  `class AverageSpendWidget : GlanceAppWidget()` with `provideGlance` →
  `provideContent { GlanceTheme { WidgetContent(context) } }`.
- State flows through `updateAppWidgetState` preferences keys
  (`averageSpendValueKey` etc.) written by a top-level
  `suspend fun updateAverageSpendWidget(context, spends, currency, startDate, endDate)`
  (line 200) called from `BudgetWidgetUpdater.update` (line 62).
- Manifest entry: `app/src/main/AndroidManifest.xml:197`
  (`android:name=".presentation.widget.AverageSpendWidgetReceiver"` +
  `android:appwidget-provider` metadata).
- Provider XML: `app/src/main/res/xml/widget_average_spend_info.xml`
  (110dp×110dp min, `glance_default_loading_layout`, preview layout,
  `updatePeriodMillis=1800000`).
- Preview layout: `app/src/main/res/layout/widget_average_spend_preview.xml`
  exists — create an analogous `widget_savings_goal_preview.xml`.
- Widget gallery pin sheet: `WidgetGallerySheet.kt` appends
  `WidgetGalleryItem(previewLayoutRes, titleRes, descriptionRes, receiver)`
  entries (line 87 for AverageSpend).

## Design

New `SavingsGoalWidget` showing: goal label, saved-so-far vs goal
(`formatCurrencySymbolOnly`, same helper the AverageSpend widget uses at
line 218), a linear progress bar (Glance `Row` with fractional `width` in a
track — copy the bar pattern from `AverageSpendBackdrop`), percent text, and
target-date line when `savingsGoalMonths` is set (ETA from
`SavingsGoalCalculator`).

### Data plumbing

- Preferences keys: `savingsGoalSavedKey`, `savingsGoalTargetKey`,
  `savingsGoalPercentKey`, `savingsGoalEtaKey` (string prefs; percent as
  precomputed string to avoid float rendering issues).
- `suspend fun updateSavingsGoalWidget(context, progress: SavingsGoalProgress?)`:
  null progress → write "no goal" sentinel (mirror `hasSpends` int-key
  pattern) and render the empty state ("Set a savings goal in Analytics").
- Call site: `BudgetWidgetUpdater.update()` — it already receives full
  `BudgetUiState` but savings progress is computed in `AnalyticsViewModel`
  from its own flows. **Simplest correct wiring:** compute the goal snapshot
  in `BudgetWidgetUpdater` from the same primitives
  (`SavingsGoalCalculator` + `userSettings.savingsPreferences` +
  income-aware saved totals via `NetSavingsCalculator` inputs already
  available in the updater's scope) — do NOT wire AnalyticsViewModel into
  widgets. If the recomputation duplicates >30 lines of logic, extract a
  shared `domain/calculator` function instead and have BOTH call it.

### Strings

`widget_savings_goal_title`, `widget_savings_goal_description`,
`widget_savings_goal_empty`, `widget_savings_goal_eta`
("On track for %1$s"), in `values/`, `values-es`, `values-fr`.

## Steps

1. Provider XML `widget_savings_goal_info.xml` + preview layout + manifest
   service/receiver entry (copy AverageSpend's block, adjust names/labels).
2. `SavingsGoalWidget.kt` (receiver + widget + `updateSavingsGoalWidget`).
3. Wire update call in `BudgetWidgetUpdater.update()`.
4. `WidgetGallerySheet` entry + pin support.
5. Strings ×3 locales; verify gate.

## Out of scope

- Watch complication of the goal (watch features excluded this round).
- Widget configuration activity (goal is set in app settings).

## Test plan

- Unit-test the pure computation feeding the widget (percent, ETA string
  choice) in `app/src/test` following `SavingsGoalCalculatorTest` style.
- Paparazzi/manual: no new golden required (Glance widgets aren't under
  Paparazzi; existing widgets have none either).
- Manual: set a goal → add widget from gallery → verify progress updates
  after adding a transaction.

## Done criteria

1. `./gradlew :app:compileFossDebugKotlin` — exit 0.
2. `grep -c SavingsGoalWidget app/src/main/AndroidManifest.xml` — ≥ 1.
3. Full verification gate green.

## Maintenance notes

- Follows the widget updater pattern; any future widget must reuse
  `WidgetCommon.kt` helpers where applicable.
- If goal prefs move from `SavingsPreferences`, update the widget's read path
  in the same commit.

## Escape hatches

- If computing saved-so-far in `BudgetWidgetUpdater` requires repository
  calls that would slow the update path (updater runs on every transaction),
  STOP and report — propose computing from the `transactions` list already
  in `BudgetUiState` instead of new queries.
