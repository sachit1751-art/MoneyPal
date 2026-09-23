# Plan 042 — Widget privacy redaction

**Status:** TODO
**Written against commit:** `48bb35b` (2026-09-23) + uncommitted plan-041 work
**Category:** feature (privacy, small)
**Depends on:** plan 041 (its maintenance note motivates this plan)
**Effort:** S · **Risk:** low

## Why

Plan 041 sets `FLAG_SECURE` while app lock is enabled, but widgets are rendered
by the launcher and are **not** protected by it. The budget/days-countdown/
savings widgets show balances on the home screen (and lock screen on API 34+)
even when the app itself is locked. A per-widget "hide amounts" toggle closes
that gap.

## Conventions (every round-6 plan)

- Strings in `values/strings.xml` **plus `values-es` and `values-fr`** (other
  locales are Crowdin-managed).
- Money math is `BigDecimal`/plain strings, never `Double`.
- Pure logic in `domain/` with JUnit4 + Truth tests.
- Conventional commits, one commit per plan.
- Verification gate:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

## Current state (verified)

- Widget formatting is centralized in
  `app/src/main/java/com/sachit/moneypal/presentation/widget/WidgetCurrencyFormatter.kt`
  with helpers in `WidgetCommon.kt`; updaters under `presentation/widget/`
  (e.g. `presentation/ui/budget/BudgetWidgetUpdater.kt`).
- Settings boolean pattern: `APP_LOCK_ENABLED_KEY_NAME` in
  `SettingsRepositoryImpl.kt` (~line 50) + `SettingsRepository` setter +
  `UserSettings` field.
- Plan 041's maintenance note explicitly calls out this gap.

## Steps

1. **Domain**: `MaskedAmountFormatter` in `domain/` —
   `format(amount: String, hide: Boolean): String` returning e.g. `$•••` when
   hiding, else the input unchanged. Keep it string-based (widgets already
   format through `WidgetCurrencyFormatter`).
2. **Settings plumbing**: DataStore boolean key `widgets_hide_amounts`
   (default `false`), repository setter, `UserSettings` field, round-trip +
   fallback coverage in `SettingsRepositoryImplTest`.
3. **Widget wiring**: in each widget update path, read the flag (via the
   existing settings flow the updaters already use) and pass amounts through
   the masked formatter. If async settings reads are awkward inside
   `AppWidgetProvider`, snapshot the flag into the update work's input data.
4. **Settings UI**: toggle inside the widgets sheet (`WidgetGallerySheet.kt`
   area), subtitle explaining why (widgets are visible even when the app is
   locked). All three locales.

## Out of scope

- Per-widget granularity, wear module, widget-side biometric gate.

## Test plan

- `MaskedAmountFormatterTest` (domain): hide on/off, empty/odd inputs.
- Repo round-trip + default-off test.
- Manual: enable toggle → update widget → amounts masked; disable → restored.

## Done criteria

1. `grep -rn "widgets_hide_amounts" app/src/main --include="*.kt"` — repo +
   widget usage sites.
2. Verification gate exit 0 including new tests.

## Escape hatches

- If any widget reads amounts outside `WidgetCurrencyFormatter`, report the
  call sites and mask there too rather than partially covering.
