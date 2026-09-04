# 003 — Route period/time logic through `TimeProvider` so tests can freeze time

**Audit base:** commit `a2fda9c` — verify with `git rev-parse HEAD` first.

## Why this matters

Budget rollover and period-boundary logic is the highest-risk code in the app
(money moves between periods at midnight), yet it reads the wall clock
directly via `LocalDate.now()` / `LocalDateTime.now()`. The repo already has a
time-abstraction seam that is barely used:

`app/src/main/java/com/sachit/moneypal/domain/time/TimeProvider.kt`:

```kotlin
interface TimeProvider {
    fun nowEpochMillis(): Long
    fun nowInstant(): Instant
}

class SystemTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
    override fun nowInstant(): Instant = Instant.ofEpochMilli(nowEpochMillis())
}
```

Bound by Hilt in `app/src/main/java/com/sachit/moneypal/data/di/TimeModule.kt`.
Only `BudgetPeriodManager` injects it — and then calls `LocalDate.now()`
anyway. Because the clock can't be frozen, midnight/DST/boundary regressions
(period already ended, rollover queued twice, notification fired for a stale
period) are only caught by luck. The fix is a pure refactor: introduce a
date-returning method with a default implementation so existing fakes keep
compiling, inject it where DI already exists, and thread `today` parameters
through receiver/worker entry points instead of calling the clock inside
their testable logic.

## Files in scope

1. `app/src/main/java/com/sachit/moneypal/domain/time/TimeProvider.kt` — add a method.
2. `app/src/main/java/com/sachit/moneypal/domain/time/MidnightPeriodChecker.kt` — inject + use it.
3. `app/src/main/java/com/sachit/moneypal/presentation/ui/budget/BudgetPeriodManager.kt` — use injected provider.
4. `app/src/main/java/com/sachit/moneypal/presentation/notification/MidnightPeriodTransitionReceiver.kt`
5. `app/src/main/java/com/sachit/moneypal/presentation/notification/PeriodEndAlarmReceiver.kt`
6. `app/src/main/java/com/sachit/moneypal/presentation/notification/RecurrentExpenseNotificationWorker.kt`
7. Tests that construct the above (search results will name them).

## Steps

### 1. Extend `TimeProvider`

Add a default method (keeps existing fakes/tests compiling):

```kotlin
interface TimeProvider {
    fun nowEpochMillis(): Long
    fun nowInstant(): Instant

    /** Today's date in the device's default zone — overridable in tests. */
    fun today(): LocalDate = nowInstant().atZone(ZoneId.systemDefault()).toLocalDate()
}
```

Add imports `java.time.LocalDate`, `java.time.ZoneId`. `SystemTimeProvider`
needs no change (default impl). `TimeModule` needs no change.

### 2. `MidnightPeriodChecker` (domain, `@Singleton`)

- Add `private val timeProvider: TimeProvider` to the constructor (Hilt
  provides it — check `MidnightPeriodCheckerTest` for direct constructions
  and update them: the test may already fake the two repositories; give it a
  fake `TimeProvider` returning a fixed `Instant`, e.g.
  `Instant.parse("2026-03-15T00:00:00Z")`).
- Replace `LocalDate.now()` at the two call sites
  (`resolveEndingPeriodState`, `handleEarlyFinish`) with
  `timeProvider.today()`.
- Keep every `ZoneId.systemDefault()` conversion as-is.

### 3. `BudgetPeriodManager`

- It already receives `timeProvider: TimeProvider` — replace
  `val now = LocalDate.now()` in `finishBudgetEarly()` with
  `val now = timeProvider.today()`.
- Check its test file (`BudgetPeriodManagerTest.kt`): if its fake implements
  `TimeProvider`, nothing changes (default method); if it uses `mockk`,
  nothing changes either. Run its tests to confirm.

### 4. Receivers and worker

These are Android entry points without DI; keep the clock out of their logic
by extracting pure, `today`-parameterized internal functions and calling them
from the receiver with `LocalDate.now()` at the boundary:

- `MidnightPeriodTransitionReceiver.kt` line ~68 (`val today = LocalDate.now()`):
  move the body that uses `today` into an `internal suspend fun handlePeriodCheck(today: LocalDate, ...)` (whatever parameters it needs) and have `onReceive` call it with `LocalDate.now()`. Do the same for any other `now` usage inside.
- `PeriodEndAlarmReceiver.kt` line ~40: same treatment.
- `RecurrentExpenseNotificationWorker.kt` line ~74: same treatment inside `doWork()` — the line that reads `today` should become a parameter of a testable internal function (or read `timeProvider`-style via a constructor-provided value; the worker is Hilt-constructed so adding a `TimeProvider` constructor param is acceptable — prefer that, matching `CsvImportWorker`'s Hilt style).
- Do **not** attempt to unit-test the receiver classes themselves; test the extracted internal functions.

### 5. Add frozen-time tests

- In `MidnightPeriodCheckerTest` add a test that pins `today()` to a fixed
  date and asserts a period whose end date is yesterday triggers
  `shouldHandleEndingPeriod` while one ending tomorrow does not (read the
  existing test file first — it may already cover this via repository fakes;
  extend rather than duplicate).
- In the worker/receiver extraction: if an existing test file covers the
  extracted function, add one boundary case (e.g. occurrence today vs
  tomorrow). If none exists and creating one requires heavy Android fakes,
  skip the test and say so in the report — do not add Robolectric.

## Done criteria

- [ ] `grep -rn "LocalDate.now()" app/src/main/java/com/sachit/moneypal/domain app/src/main/java/com/sachit/moneypal/presentation/notification app/src/main/java/com/sachit/moneypal/presentation/ui/budget/BudgetPeriodManager.kt`
      returns **no** hits inside the classes in scope (boundary calls in
      `onReceive`/`doWork` that immediately delegate are allowed and expected).
- [ ] All unit tests pass:
      `./gradlew :app:testFossDebugUnitTest --tests "com.sachit.moneypal.domain.time.MidnightPeriodCheckerTest" --tests "com.sachit.moneypal.presentation.ui.budget.BudgetPeriodManagerTest"`
- [ ] Full compile: `./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :wear:compileDebugKotlin :sync-contract:compileKotlin` → `BUILD SUCCESSFUL`.

## Maintenance note

- New time-dependent logic should call `TimeProvider` (injected) or accept a
  `today` parameter — never `LocalDate.now()` inside a function that is, or
  could be, unit-tested.
- UI previews (`@Preview` blocks using `LocalDate.now()`) and formatting
  helpers (`DateFormatting.kt`) are out of scope and may keep using the clock.

## Escape hatches

- If a receiver's logic can't be cleanly extracted without changing manifest
  wiring or PendingIntent semantics, STOP and report the specific receiver —
  do not redesign the broadcast flow.
- If `MidnightPeriodCheckerTest` constructs the checker with `mockk`-relaxed
  repos and the new constructor param breaks it in a way that forces mocking
  `TimeProvider` with strict stubs everywhere, prefer adding a tiny
  `FakeTimeProvider(instant: Instant)` test helper in the test source set
  (one file, reused by all three test classes).

## Out of scope / boundaries

- No behavior change to scheduling math; this is a refactor + tests.
- Do not touch `NotificationScheduler` (its time reads already flow through
  injectable parameters in part — leave for a later pass).
- Do not touch `wear/` or `sync-contract/`.
