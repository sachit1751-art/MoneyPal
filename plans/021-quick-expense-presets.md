# Plan 021 — Quick-amount presets on the numpad

**Status:** TODO
**Written against commit:** `ff6f773` (2026-09-16)
**Category:** feature
**Depends on:** nothing
**Effort:** M · **Risk:** low (UI slot already exists; logic is a stored list)

## Why

The numpad already renders a **quick-amounts row** when non-empty
(`Numpad.kt:213` — `if (quickAmounts.isNotEmpty() && onQuickAmount != null && !isCalculation)`),
and the MVI path exists end-to-end (`BudgetNumpadIntent.QuickAmountTapped` →
`NumpadController.handleQuickAmount`, which replaces the current entry). But
**nobody ever populates the list**: `MainScreenContent.kt:1354` passes
`quickAmounts = remember { listOf(...) }` as a hardcoded local demo that
doesn't reflect user behavior, and there is no way to customize it.

Feature: learn the user's most-frequent **exact amounts** and offer them as
one-tap chips; let the user pin custom presets. Tapping inserts the amount
into the current entry (existing behavior).

## Current state (excerpts verified)

`app/src/main/java/com/sachit/moneypal/presentation/ui/budget/controller/NumpadController.kt:50`

```kotlin
private fun handleQuickAmount(amount: BigDecimal, currentIsCalculation: Boolean): List<NumpadChange> {
```

`app/src/main/java/com/sachit/moneypal/presentation/ui/home/MainScreenContent.kt:1354`

```kotlin
quickAmounts = remember { listOf(
```

## Design

1. **Domain — pure picker** (testable, follows `CategorySuggester` pattern):
   `domain/calculator/QuickAmountPicker.kt`

   ```kotlin
   class QuickAmountPicker @Inject constructor() {
       /**
        * Returns up to [max] frequently-used exact amounts from [history],
        * most-frequent first, ties broken by recency. Only amounts used
        * >= [minUses] times qualify. Ignores credits, adjustments and
        * recurring transactions (recurring amounts are auto-entered anyway).
        */
       fun pick(
           history: List<TransactionModel>,
           minUses: Int = 3,
           max: Int = 4,
       ): List<BigDecimal>
   }
   ```

   Implementation notes: group by `amount` (BigDecimal equality is fine —
   values come from Room TEXT and are parsed with the project's standard
   `toBigDecimalOrZero()`-style helpers; see `TransactionModel`), filter
   `!isCredit && !isAdjustment && !isRecurrent`, sort by (count desc,
   latest `date` desc), take `max`, and **strip trailing zeros** so `50.00`
   and `50` merge (use `stripTrailingZeros()` on both sides of grouping).
2. **Settings** — `quick_presets` string in DataStore via
   `SettingsRepository` (comma-separated custom presets the user pinned;
   empty by default). Follow the existing
   `SettingsRepositoryImpl` boolean/string key pattern (see
   `WEEKLY_DIGEST_ENABLED_KEY_NAME` at line 50 for the constant style).
3. **ViewModel** — in `BudgetViewModel`, combine repository history flow with
   the presets preference:
   `quickAmounts = customPresets + QuickAmountPicker.pick(history)`, capped,
   deduped (custom first). Expose via existing UI state field already
   consumed by `MainScreenContent`.
4. **UI wiring** — replace the hardcoded `remember { listOf(...) }` in
   `MainScreenContent.kt:1354` with the state value. The numpad row itself
   needs **no changes**.
5. **Preset management (minimal)** — long-press on a quick-amount chip opens
   the existing delete-confirmation dialog pattern (`NumpadButton.kt` uses
   plain clickables; mirror the long-press → dialog pattern from
   `SwipeableExpenseItem.kt`). Actions: "Pin current amount" (adds the
   currently-entered value to custom presets) and "Remove" per chip. Strings:
   `quick_preset_pin`, `quick_preset_remove`, `quick_presets_title`.

## Steps

1. `QuickAmountPicker` + `QuickAmountPickerTest` (follow
   `CategorySuggesterTest.kt` style — plain JUnit4 + Truth, constructor
   injection-free). Cases: frequency ranking, tie→recency, minUses cutoff,
   credit/adjustment/recurring exclusion, `50.00` vs `50` merge, empty
   history → empty list, cap at `max`.
2. Settings key + repository methods (`getQuickPresets`/`setQuickPresets`)
   following an existing string-setting pair exactly.
3. ViewModel wiring + replace hardcoded list in `MainScreenContent.kt`.
4. Long-press pin/remove UI + strings in `values/strings.xml` **and**
   `values-es`, `values-fr` (Crowdin picks up later, but the two maintained
   locales must not fall back to English in releases).
5. Run verification gate.

## Out of scope

- Widget-side quick amounts (glance widgets have their own entry flow).
- Watch numpad (excluded per maintainer).
- Cloud sync of presets (no account system exists).

## Test plan

- Unit: `QuickAmountPickerTest` (cases above).
- Existing `NumpadControllerTest` must stay green — `handleQuickAmount`
  behavior is unchanged.
- Manual: enter 3+ transactions of the same amount → chip appears; tap chip
  → entry shows the amount; long-press → pin/remove dialog.

## Done criteria

1. `./gradlew :app:testFossDebugUnitTest --tests "*QuickAmountPickerTest"` — all pass.
2. `grep -n "remember { listOf" app/src/main/java/com/sachit/moneypal/presentation/ui/home/MainScreenContent.kt` — 0 matches near the `quickAmounts` param (line ~1354); the param comes from UI state.
3. Full verification gate (index README) — green.

## Maintenance notes

- If a future "spending suggestions" feature lands, it should reuse
  `QuickAmountPicker` rather than duplicating frequency logic.
- The DataStore key format (`quick_presets`) is user data — if the format
  ever changes, add migration in `SettingsRepositoryImpl`, not a new key.

## Escape hatches

- If `TransactionModel` amount parsing makes grouping unreliable (mixed
  decimal separators), STOP and report — do not normalize silently inside
  the picker; surface the helper that already exists in the codebase.
