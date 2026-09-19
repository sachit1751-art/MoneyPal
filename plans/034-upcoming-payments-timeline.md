# Plan 034 — Upcoming payments timeline (subscriptions + card dues + period end)

**Status:** TODO
**Written against commit:** `467ce9d` (2026-09-19) — round 5 (features)
**Category:** feature (small–medium)
**Depends on:** nothing
**Effort:** S–M · **Risk of fix:** low

## Why

The app knows about three kinds of future money-out — upcoming recurrent
expenses, credit-card due dates, and the period end — but shows them in
separate places (History's upcoming-recurrent section; settings notifications)
with no single "what's coming and how much is committed" view. A unified
timeline answers "can I afford the rest of this period?" in one glance, which
no existing screen does.

## Conventions (every round-5 plan)

- User-facing strings in `app/src/main/res/values/strings.xml` **plus
  `values-es` and `values-fr`** (other locales are Crowdin-managed).
- Money = `BigDecimal`/plain strings, never `Double`.
- Pure logic in `domain/` with JUnit4 + Truth tests.
- Conventional commits, one commit for this plan.
- Global verification gate:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

## Current state (verified)

- History already projects upcoming recurrents into the list:
  `presentation/ui/theme/component/expense/UpcomingRecurrentItem.kt` exists
  and `HistoryScreenE2ETest.kt` lines 127-171 feed
  `upcomingRecurrentInPeriod` into the History state
  (`HistoryMviContract.kt` ~line 70 region). Read
  `HistoryCalculations.kt` (has `previousPeriodId`-based filtering) to see
  how upcoming items are derived today.
- Recurrence next-occurrence math exists in the scheduling path
  (`RecurrentExpenseNotificationWorker` computes next dates; PROJECT_OVERVIEW
  "WorkManager" section). Extract/reuse — do not rewrite recurrence math in
  the timeline.
- Credit-card due dates: `Transaction.isCredit`/`isCreditPaid` +
  `subscriptionDay` (schema 23) with a `creditCardCutoffDay` on the budget
  entity — find the existing calculator for card dues via
  `grep -rn "creditCardCutoffDay\|CutoffCalculator" app/src/main --include="*.kt"`.
- Period end: budget settings carry `endDate` (schema 23 `endDate` column).
- Date formatting helpers exist under `presentation/util/` (upcoming strings
  "Today"/"Tomorrow"/"In N days" at strings.xml lines 569-573) — reuse.

## Steps

### Step 1 — Domain: unified timeline model

New `app/src/main/java/com/sachit/moneypal/domain/calculator/UpcomingTimelineCalculator.kt`:

- Inputs: active (and paused-excluded) recurring templates with frequency +
  subscription day; credit items with `isCreditPaid=false` and their cutoff
  day; period end date; today; horizon (rest of current period, capped at 60
  days).
- Output: `UpcomingTimeline(entries: List<UpcomingEntry>, committedTotal:
  BigDecimal)` where `UpcomingEntry(date: LocalDate, label, amount:
  BigDecimal, kind: RECURRING|CARD_DUE|PERIOD_END, transactionId: Long?)`
  sorted by date, PERIOD_END rendered as its own row (no amount or as the
  period's remaining — pick and document; recommend no amount since remaining
  is shown elsewhere).
- `committedTotal` = sum of RECURRING + CARD_DUE amounts within horizon.
  All BigDecimal.
- Occurrence projection must reuse the same rules as the notification
  scheduler for the template→next-date mapping (extract that pure function if
  it's currently embedded in the worker; if extraction is risky, compute
  independently and add a test asserting both agree for a 90-day matrix of
  frequencies/days).

### Step 2 — Surface the timeline

- History screen, above the existing upcoming-recurrent section: replace that
  section's data source with the unified timeline (keep the existing
  `UpcomingRecurrentItem` row visuals, add a small kind badge for card dues
  and the period-end row). Committed total as a section header line.
  Rationale: History is where "what's coming" already lives; a second
  top-level destination is over-scope for this round.
- MVI: extend `HistoryMviContract` state with `upcomingTimeline` and compute
  it in the same combine() that builds `upcomingRecurrentInPeriod` today.

### Step 3 — Strings

`upcoming_timeline_*` (header, committed-total line, kind badges) in all
three locales. Reuse `upcoming_recurrent_today/tomorrow/in_days` for date
labels.

## Out of scope

- Editing/paying from the timeline (mark-paid from notifications is plan
  040's job).
- A standalone calendar screen (plan 031 covers spatial view of the past;
  this covers the future).
- Wear module (phone History only).

## Test plan

- `UpcomingTimelineCalculatorTest.kt`: weekly + monthly templates across a
  month boundary; subscriptionDay=31 in February (clamp to month length —
  verify existing scheduler behavior and match it); paused templates
  excluded; already-paid credit items excluded; committedTotal sums exactly
  (BigDecimal); horizon cap (no entries beyond 60 days); empty state.
- Cross-check test vs scheduler math if extraction was not done (see Step 1).

## Done criteria (machine-checkable)

1. `./gradlew :app:testFossDebugUnitTest` — exit 0 including
   `UpcomingTimelineCalculatorTest`.
2. Compile gate — exit 0.
3. Manual: with one monthly subscription, one unpaid card due, and a period
   ending this month → three rows in date order + correct committed total;
   paused subscription absent.

## Maintenance notes

- The kind enum is the seam for future surfaces (widget row, wear glance) —
  keep `UpcomingTimeline` Android-free.
- If the scheduler math is extracted (Step 1), the extraction is the
  maintenance win: scheduler and timeline can no longer drift.

## Escape hatches

- If the existing scheduler's next-occurrence math cannot be extracted or
  matched (e.g. it depends on WorkManager state), STOP and report — a
  timeline that disagrees with actual notifications is worse than no
  timeline.
- If History's state combine() is already at its complexity limit, surface
  the timeline in Budget instead and note the relocation in the commit body.
