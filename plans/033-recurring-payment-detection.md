# Plan 033 — Recurring-payment detection from history + one-tap template

**Status:** TODO
**Written against commit:** `467ce9d` (2026-09-19) — round 5 (features)
**Category:** feature (medium)
**Depends on:** nothing (complements plan 030's dedupe hash; independent)
**Effort:** M · **Risk of fix:** low

## Why

Users record the same subscriptions manually for months before thinking to
mark them recurring. The transaction history already contains the signal:
same normalized comment, similar amount, regular spacing. Detecting these and
offering a one-tap "make recurring" converts manual drudgery into automation —
and the notification/reminder system MoneyPal already has does the rest.

This is deliberately **history-only** detection: no bank connections, no SMS
data beyond what's already stored. Pure domain math + one suggestion card.

## Conventions (every round-5 plan)

- User-facing strings in `app/src/main/res/values/strings.xml` **plus
  `values-es` and `values-fr`** (other locales are Crowdin-managed).
- Money = `BigDecimal`/plain strings, never `Double`.
- Pure logic in `domain/` with JUnit4 + Truth tests (pattern:
  `QuickAmountPicker.kt` + its test — this plan's closest sibling).
- Conventional commits, one commit for this plan.
- Global verification gate:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

## Current state (verified)

- Recurring model on `Transaction`:
  `domain/model/TransactionModel.kt` lines 21-25 (`isRecurrent`,
  `recurrentFrequency: RecurrentFrequency?`, `recurrentEndDate`,
  `subscriptionDay`); paused flag at lines 53-55 (`isRecurrentPaused`).
  `RecurrentFrequency` enum lives in the same package — read it (weekly /
  biweekly / monthly; verify) before mapping detection output.
- Precedent for "learned from history" features: `QuickAmountPicker.kt`
  (domain/calculator/) with `MIN_USES`-style thresholds and its test — mirror
  its structure (pure object, data class result, conservative thresholds).
- Dismissal-persistence precedent: `EnvelopeAlertObserver` persists
  per-key booleans in DataStore (`ALERTED_KEY_PREFIX`) — suggestions need the
  same (dismissed suggestions must never reappear).
- Where suggestions surface: Budget main screen already hosts learned-UI
  elements (plan 021 quick amounts) and Analytics hosts cards; the suggestion
  card belongs near the recurring list UI — locate it via
  `grep -rn "recurrent" app/src/main/java/com/sachit/moneypal/presentation/ui/budget --include="*.kt" -l`
  and pick the list's empty-state or top section.

## Steps

### Step 1 — Domain detector

New `app/src/main/java/com/sachit/moneypal/domain/calculator/RecurringPatternDetector.kt`:

- Normalize comments: lowercase, strip digits/punctuation, collapse spaces.
- Group by normalized comment; within groups, cluster by amount similarity
  (±10% or ±1.00 of group median — pick and document one).
- For each qualifying group (≥3 occurrences in the last 90 days), compute the
  median inter-occurrence gap in days; map gap → candidate
  `RecurrentFrequency` (≤2: skip — daily noise; 5–9: WEEKLY; 12–16: BIWEEKLY;
  25–35: MONTHLY; else: none). Confidence = share of gaps fitting the mapped
  frequency (≥0.8 required).
- Output `RecurringSuggestion(normalizedComment, sampleComment, amount:
  BigDecimal, frequency, nextExpectedDate, confidence: Int, occurrences:
  Int)`. Exclude any comment whose normalized form already belongs to an
  active or paused recurring transaction (caller passes those in).
- Amount for the template: median of group.

### Step 2 — Dismissal persistence + use case

- `SuggestionDismissalStore` (DataStore, pattern:
  `EnvelopeAlertObserver.ALERTED_KEY_PREFIX`): key
  `recurring_suggestion_dismissed_<hash(normalizedComment)>`.
- `SuggestRecurringUseCase` in `domain/usecase/`: takes recent transactions +
  existing recurring templates, returns suggestions minus dismissed.

### Step 3 — UI

- A dismissible suggestion card (title = sample comment, amount, proposed
  frequency, "Make recurring" / "Dismiss" buttons) where the recurring list
  lives. "Make recurring" pre-fills the recurring editor with
  comment/amount/frequency/subscriptionDay = next-expected day-of-month and
  opens it (do not silently create the template — user confirms amount/date).
  "Dismiss" persists the dismissal.
- One card at a time (highest confidence); after action, recompute.
- Strings `recurring_suggestion_*` in all three locales.

## Out of scope

- Detecting from SMS data (SMS capture already handles bank-side detection
  with its own pipeline — plan 012-015 territory).
- Variable-amount subscriptions (e.g. utilities) — the ±10% rule will
  naturally miss them; fine for v1.
- Auto-creating templates without confirmation.
- Wear module.

## Test plan

- `RecurringPatternDetectorTest.kt`: weekly pattern detected; biweekly;
  monthly spanning different month lengths (28→31 gap tolerance via the
  25–35 window); ±10% amount clustering; <3 occurrences → none; irregular
  gaps → confidence below threshold → none; existing recurring comments
  excluded; nextExpectedDate math.
- Use-case test: dismissed suggestion filtered out; highest-confidence-first
  ordering.

## Done criteria (machine-checkable)

1. `./gradlew :app:testFossDebugUnitTest` — exit 0 including
   `RecurringPatternDetectorTest`.
2. Compile gate — exit 0.
3. Manual: record "Netflix 15.99" weekly ×4 in test data → suggestion card
   appears with WEEKLY + correct next date; dismiss → card never returns
   (across app restarts).

## Maintenance notes

- Thresholds are deliberately conservative; log (logcat debug) when a group
  nearly qualifies so future tuning has data without new analytics.
- If plan 015's SMS review inbox later wants "suggest recurring from
  accepted SMS captures", it should call this detector, not fork it.

## Escape hatches

- If normalized-comment grouping explodes combinatorially on large histories
  (test 2k rows), add a pre-filter (only comments appearing ≥3 times) and
  report the change.
- If `RecurrentFrequency` lacks a fit (e.g. no monthly-day-anchored variant),
  STOP and report rather than widening the enum silently — enum changes
  ripple into Room serialization and backups.
