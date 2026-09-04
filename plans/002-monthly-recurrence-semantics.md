# 002 — Unify monthly recurrence: month-end clamping in "due today" and scheduler

**Audit base:** commit `a2fda9c` — verify with `git rev-parse HEAD` first.

## Why this matters

Monthly recurring expenses are a headline feature, and two independent pieces
of code disagree about when a monthly bill is due for billing days 29–31
(months that lack that day):

- `RecurringExpenseCalculator.isRecurringDueToday` (domain) requires the exact
  day-of-month: `today.dayOfMonth == billingDay`. For a bill due on the 31st,
  February never fires, and the bill is silently absent from "due today"
  budget sums for that month.
- `NotificationScheduler.nextMonthlyOccurrence` (presentation) **clamps**:
  `subscriptionDay.coerceIn(1, today.lengthOfMonth())`, so the notification
  fires on Feb 28 for a 31st-day bill.

Result: in short months the user gets a *notification* on the clamped day but
the *budget math* never counts that expense as due that day (or vice versa in
other flows). The two must share one definition.

## Chosen semantics (canonical)

A monthly occurrence for billing day `D` falls on
`min(D, lengthOfMonth)` of the occurrence month — i.e. the 31st is billed on
the last day of shorter months (Feb 28/29, Apr 30, …), matching what the
notification scheduler already does. The "due today" predicate must produce
the same dates the scheduler produces. No skipping, no shifting into the next
month.

## Current state

`app/src/main/java/com/sachit/moneypal/domain/calculator/RecurringExpenseCalculator.kt`,
MONTHLY branch of `isRecurringDueToday` (≈lines 64–67):

```kotlin
RecurrentFrequency.MONTHLY -> {
    val billingDay = transaction.subscriptionDay ?: startDate.dayOfMonth
    today.dayOfMonth == billingDay
}
```

`app/src/main/java/com/sachit/moneypal/presentation/notification/NotificationScheduler.kt`,
`nextMonthlyOccurrence` (internal, ≈lines 286–295):

```kotlin
internal fun nextMonthlyOccurrence(
    startDate: LocalDate,
    today: LocalDate,
    subscriptionDay: Int,
): LocalDate {
    var candidate = today.withDayOfMonth(subscriptionDay.coerceIn(1, today.lengthOfMonth()))
    if (candidate.isBefore(today)) {
        val nextMonth = today.plusMonths(1)
        candidate =
            nextMonth.withDayOfMonth(subscriptionDay.coerceIn(1, nextMonth.lengthOfMonth()))
    }
    return if (candidate.isBefore(startDate)) startDate else candidate
}
```

Existing tests you must keep green / extend:
- `app/src/test/java/com/sachit/moneypal/domain/calculator/RecurringExpenseCalculatorTest.kt`
  (JUnit4, plain `Assert.*`).
- `app/src/test/java/com/sachit/moneypal/presentation/notification/NotificationSchedulerTest.kt`
  (read it first — it covers `nextOccurrenceDateTime`/`nextMonthlyOccurrence`).

## Steps

### Phase A — characterization tests (write first, they must pass against current code)

1. Open `RecurringExpenseCalculatorTest.kt` and add a test group for billing
   days 29–31. Use the existing `recurrentTransaction(...)` helper and a
   `subscriptionDay = 31` transaction started `2026-01-31` (any non-leap-safe
   month). Assert **current** behavior first (this documents the bug):
   `assertFalse(isRecurringDueToday(tx, LocalDate.of(2026,2,28)))` for day 31.
   Run: `./gradlew :app:testFossDebugUnitTest --tests "com.sachit.moneypal.domain.calculator.RecurringExpenseCalculatorTest"`.
   Confirm the new test passes (documenting current behavior) — this is the
   characterization baseline.

2. Add cases you will assert **after** the fix (write them now, expect them to
   fail until Phase C — or skip asserting until Phase C if you prefer a green
   tree at each step; the index only requires the final tree green):
   - day 31 → due on Feb 28, and on Apr 30, and on May 31;
   - day 30 → due on Feb 28 (2026 not a leap year) and Feb 29 (2028);
   - day 29 → due on Feb 28 in 2026;
   - default billing day = start date's day-of-month keeps working (existing tests cover 15).

### Phase B — single source of truth

3. In `RecurringExpenseCalculator.kt` add a public/internal pure function that
   both call sites will use, e.g.:

   ```kotlin
   /** Monthly occurrence day, clamping billing day D to the month's length (31st → Feb 28/29). */
   fun monthlyOccurrenceDay(billingDay: Int, month: YearMonth): Int =
       billingDay.coerceIn(1, month.lengthOfMonth())
   ```

   (Add `java.time.YearMonth` import.) Keep it on `RecurringExpenseCalculator`
   so the domain owns the rule.

4. Change the MONTHLY branch of `isRecurringDueToday` to:

   ```kotlin
   RecurrentFrequency.MONTHLY -> {
       val billingDay = transaction.subscriptionDay ?: startDate.dayOfMonth
       val expectedDay = monthlyOccurrenceDay(billingDay, YearMonth.from(today))
       // Only fire once the occurrence month is actually reachable: the
       // recurrence starts in startDate's month, and today must be at or
       // past the first possible occurrence.
       !today.isBefore(startDate) && today.dayOfMonth == expectedDay
   }
   ```

   Keep the surrounding guards (`endDate`, `today.isBefore(startDate)`) that
   already exist above the `when` — do not duplicate them.

5. Make `NotificationScheduler.nextMonthlyOccurrence` delegate to the same
   rule so the two can never drift again. In `NotificationScheduler.kt`
   replace the body with calls to the domain function, e.g.:

   ```kotlin
   internal fun nextMonthlyOccurrence(startDate: LocalDate, today: LocalDate, subscriptionDay: Int): LocalDate {
       val billingDay = subscriptionDay.coerceIn(1, 31)
       var candidate = today.withDayOfMonth(
           RecurringExpenseCalculator().monthlyOccurrenceDay(billingDay, YearMonth.from(today))
       )
       if (candidate.isBefore(today)) {
           val nextMonth = today.plusMonths(1)
           candidate = nextMonth.withDayOfMonth(
               RecurringExpenseCalculator().monthlyOccurrenceDay(billingDay, YearMonth.from(nextMonth))
           )
       }
       return if (candidate.isBefore(startDate)) startDate else candidate
   }
   ```

   Prefer making `monthlyOccurrenceDay` `internal` and (if the codebase style
   allows) a top-level/object function to avoid instantiating the calculator
   in the scheduler; match whatever the surrounding code does for sharing
   logic between modules (both classes are `@Inject`-constructed elsewhere —
   check `NotificationSchedulerTest` for how it constructs the scheduler and
   keep its constructor unchanged).

### Phase C — fix assertions, verify

6. Flip the Phase A-1 assertion (and any Phase A-2 case you wrote asserting old
   behavior) to assert the **clamped** behavior, and run the two test classes:

   ```bash
   ./gradlew :app:testFossDebugUnitTest \
     --tests "com.sachit.moneypal.domain.calculator.RecurringExpenseCalculatorTest" \
     --tests "com.sachit.moneypal.presentation.notification.NotificationSchedulerTest"
   ```

7. Compile everything: `./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :wear:compileDebugKotlin :sync-contract:compileKotlin` — expect `BUILD SUCCESSFUL`.

## Done criteria

- [ ] `monthlyOccurrenceDay` (or equivalent) lives in `RecurringExpenseCalculator.kt` and both the calculator's MONTHLY branch and `NotificationScheduler.nextMonthlyOccurrence` derive their dates from it (no independent clamp/equality logic left).
- [ ] `RecurringExpenseCalculatorTest` covers 29/30/31 across Feb (non-leap), leap Feb, and a 30-day month, asserting clamped due dates.
- [ ] `NotificationSchedulerTest` still passes unchanged in intent (if its expectations encoded the old clamp, they must still hold — the clamp semantics are preserved).
- [ ] The two `--tests` runs above are green.
- [ ] Full compile gate green.

## Test plan

Additions described in Phase A. Follow the file's existing JUnit4 style
(`org.junit.Test` + `Assert.*`). Add at least one test proving calculator and
scheduler agree for a 31st-day bill across Feb 2026 → Mar 2026 (assert the
scheduler's returned date equals the calculator's due date for the same
month).

## Maintenance note

- Future recurrence work (e.g. "next charge" forecasts,
  `UpcomingRecurrentItemRow`, `RecurrentItemsContent`) should call the domain
  functions in this plan rather than re-deriving day-of-month math.
- If a product decision ever changes semantics to "skip the month" or "shift
  to next month", it changes in exactly one file (`RecurringExpenseCalculator.kt`).

## Escape hatches

- If `NotificationSchedulerTest` encodes the old behavior in a way that
  conflicts with delegation (e.g. mocks the calculator), STOP and report back
  with the failing test name rather than weakening the test.
- If you find *other* call sites computing monthly occurrence (grep
  `dayOfMonth ==|subscriptionDay` across `app/src/main` and `wear/src/main`),
  do not refactor them silently — list them in your report and stop.

## Out of scope / boundaries

- No changes to `wear/` or `sync-contract/` unless the grep above proves they
  duplicate the math (then stop and report).
- No UI/string changes. No DB schema changes.
