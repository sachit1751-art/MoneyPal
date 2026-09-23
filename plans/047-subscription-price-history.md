# Plan 047 — Subscription price-change detection

**Status:** TODO
**Written against commit:** `48bb35b` (2026-09-23)
**Category:** feature (S)
**Depends on:** —
**Effort:** S · **Risk:** low

## Why

Recurring/subscriptions are first-class (`isRecurrent`, `subscriptionDay`),
but when a subscription's amount changes (streaming price hikes, plan
upgrades), the change is silent. Users only notice when the budget overshoots.
A "price changed" badge + one-tap update on the recurring template closes the
gap without any new schema.

## Current state (verified)

- Recurring templates are `Transaction` rows with `isRecurrent = true` and a
  `recurrentFrequency` (`WEEKLY`/`BIWEEKLY`/`MONTHLY`); paid occurrences are
  tracked separately in `PaidRecurrentOccurrence`.
- History already has single-entry duplicate + edit flows
  (`HistoryViewModel.kt` line 325 duplicate helper).
- Recurring payments view mode exists in History
  (`RecurrentPaymentsViewMode` imported in Settings).

## Steps

1. **Domain**: `PriceChangeDetector` — pure: given a recurring template
   (current amount) and its paid occurrences with amounts, return
   `PriceChange(previousAmount, newAmount, changedAt)` when the **last ≥2
   occurrences** differ from the template amount and match each other (i.e.
   two consecutive charges at the new price = confident change; one differing
   charge = "possible change" variant). `BigDecimal` equality via
   `compareTo` (plan 024 lesson: never compare money as floats/strings).
   Truth tests for all four shapes (no change, possible, confirmed,
   occurrence gap).
2. **UI**: badge on the recurring-payment list row ("Price changed $X → $Y")
   with a one-tap "Update template amount" action calling the existing edit
   path; dismiss = keep old template (stored as a dismissed marker in memory
   only — no persistence in this plan).
3. **Strings** ×3 locales.

## Out of scope

- Automatic template updates, notifications for price changes (follow-up),
  detecting decreases only (both directions detected).

## Test plan

- `PriceChangeDetectorTest` covering the four shapes + `compareTo` equality
  (e.g. "10.0" vs "10.00").
- ViewModel test for badge visibility + update action wiring.

## Done criteria

1. `grep -rn "PriceChangeDetector" app/src/main --include="*.kt"` — domain +
   history references.
2. Verification gate exit 0.

## Escape hatches

- If occurrence amounts aren't retrievable for old rows (pre-025 restore
  remaps), scope detection to occurrences after the plan-025 landing and
  report the limitation in the PR.
