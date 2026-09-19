# Plan 038 — Custom & payday-aligned budget periods

**Status:** TODO
**Written against commit:** `467ce9d` (2026-09-19) — round 5 (features)
**Category:** feature (medium)
**Depends on:** round-4 plan 028 should land first (period-transition logic is
backup/restore-adjacent; characterization tests protect it). Technically
independent.
**Effort:** M · **Risk of fix:** medium (touches period math used everywhere)

## Why

Real budgets follow paychecks, not calendar months: a user paid on the 25th
runs their budget from the 25th to the 24th; a freelancer runs 2-week sprints
on arbitrary anchors; some want a strict 10-day sprint. The period system
supports only DAILY / WEEKLY / BIWEEKLY / MONTHLY (`BudgetPeriod` enum,
`BudgetPeriodModel.kt` line 11) — payday-aligned monthly and custom-length
periods are the top structural gap in period UX. The budget is already stored
with explicit `startDate`/`endDate` (schema 23), so the math surface is
small: period **generation and rollover**, not storage.

## Conventions (every round-5 plan)

- User-facing strings in `app/src/main/res/values/strings.xml` **plus
  `values-es` and `values-fr`** (other locales are Crowdin-managed).
- Money = `BigDecimal`/plain strings, never `Double`.
- Pure date math in `domain/` with JUnit4 + Truth tests.
- Conventional commits, one commit for this plan.
- Global verification gate:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

(Copy the two-line gate from plans/README.md verbatim in the final file —
compile + unit test lines.)

## Current state (verified)

- `BudgetPeriod` enum (DAILY, WEEKLY, BIWEEKLY, MONTHLY) +
  `RemainingBudgetStrategy` + `BudgetSplitMode` in
  `domain/model/BudgetPeriodModel.kt` (lines 11-31).
- Budget settings entity carries `period`, `startDate`, `endDate`,
  `daysInPeriod`, `rollOverEnabled/CarryForward/Limit`, `splitMode`
  (schema 23 json, budgets table createSql).
- Period transition engine: `BudgetPeriodManager.kt` (~line 112: closes a
  period via `archivePeriod(previousPeriodId, ...)` on boundary crossing) and
  the midnight receiver described in PROJECT_OVERVIEW.md. **This is the file
  where next-period generation lives** — read all of it before changing
  anything.
- Period length math: find the pure calculator via
  `grep -rn "daysInPeriod" app/src/main/java/com/sachit/moneypal/domain --include="*.kt"`
  (there is a period calculator family; reuse).
- UI: `BudgetPeriodSheet.kt` (~653-930) chooses period type + start date and
  previews `previousPeriodDays`.

## Steps

### Step 1 — Domain: period spec

New `app/src/main/java/com/sachit/moneypal/domain/model/PeriodSpec.kt`:

```kotlin
sealed interface PeriodSpec {
    data class Standard(val period: BudgetPeriod) : PeriodSpec
    data class MonthlyAnchored(val anchorDay: Int) : PeriodSpec   // 1..31, clamps to month length
    data class FixedLength(val lengthDays: Int) : PeriodSpec      // 2..365
}
```

Plus `PeriodDateMath` (pure object):

- `nextStart(currentStart: LocalDate, spec: PeriodSpec): LocalDate`
  (MONTHLY anchor: same day next month clamped — Jan 31 → Feb 28; FIXED:
  start + lengthDays; STANDARD: delegate to existing per-period math).
- `previousStart`, `periodEnd(start, spec) = nextStart - 1 day`,
  `daysInPeriod(start, spec)`.
- Exhaustive `when` over the sealed interface — adding a new kind later is a
  compile error, not a silent bug.

### Step 2 — Wire the spec into the budget

- **Binding decision:** keep the Room schema at version 23 — no migration.
  Encode the spec in the existing `period` TEXT column as
  `MONTHLY_ANCHORED_25` / `FIXED_14` strings; `BudgetPeriod`-valued parsing
  sites get a tolerant mapper (`BudgetPeriod` values parse as today; unknown
  → MONTHLY, mirroring `RestoreBackupUseCase`'s enum fallback pattern at
  lines 219-223). List every parsing site: run
  `grep -rn "BudgetPeriod.valueOf\|BudgetPeriod\." app/src/main --include="*.kt"`
  and update each to the mapper.
- `BudgetSettings` domain model gains `periodSpec: PeriodSpec` derived from
  the stored string (keep the existing `period: BudgetPeriod` field populated
  for display compatibility where cheap; if the two-field duality gets
  messy, refactor call sites to the spec and note it).
- Rollover, split-mode, daily-allowance math switch from
  `period.toDays()`-style switch statements to `PeriodDateMath` (mechanical
  replacement; behavior identical for the four standard values — prove with
  existing tests).

### Step 3 — Transition engine + midnight receiver

`BudgetPeriodManager` uses `PeriodDateMath.nextStart` to derive the next
period's start (anchored monthly: clamp correctly across month lengths; fixed:
exact). The midnight receiver needs no change if it delegates to the manager —
verify, and report if it duplicates date math.

### Step 4 — Backup/restore

- `BackupModels.kt`: the budget settings block already serializes the period
  string — `MONTHLY_ANCHORED_25` flows through as an opaque string. Restore
  tolerance: unknown period strings → MONTHLY (same mapper). Document that
  backups from newer versions restoring into older app versions degrade to
  MONTHLY (acceptable, note in plan only).
- Plan 028's characterization tests must stay green — they pin restore
  behavior.

### Step 5 — UI

`BudgetPeriodSheet`: add "Anchored" and "Custom length" options — anchored
picker (day-of-month 1–31 with a note "clamped in short months"), custom
length picker (days, 2–365, default 14). Preview line shows the next three
period starts using `PeriodDateMath` (trust builder: users see the clamping
happening before committing). Strings `period_spec_*` in all three locales.

## Out of scope

- Multiple simultaneous budget tracks with different specs.
- Changing archived-period history (old periods keep their stored dates).
- Wear module (period payload `:sync-contract` unchanged — the watch shows
  remaining/daily only; verify `WearSyncProtocol` needs no field for spec).

## Test plan

- `PeriodDateMathTest.kt`: anchored day 25 across Feb (25th exists), day 31
  Jan→Feb clamp to 28/29 (leap + non-leap), day 30 → Feb; fixed 14-day chain
  over a DST boundary (dates are LocalDate — no DST effect; assert anyway);
  standard values delegate identically to old math (golden comparison across
  2026–2027 date range); end = nextStart − 1.
- `BudgetSettingsMapperTest`: round-trip string↔spec; legacy values;
  unknown-string fallback.
- Existing `BudgetStateCalculatorTest` + period tests stay green untouched
  (the strongest regression signal).

## Done criteria (machine-checkable)

1. `./gradlew :app:testFossDebugUnitTest` — exit 0 including
   `PeriodDateMathTest`.
2. Compile gate — exit 0.
3. `grep -rn "MONTHLY_ANCHORED_\|FIXED_" app/src/main --include="*.kt"` —
   mapper + sheet references only (no parsing sites missed: the mapper is
   the single decode point).
4. Manual: set anchored-on-25 budget → period shows 25th→24th; advance
   device date past the 24th → next period opens on the 25th; set 14-day
   custom → two consecutive periods roll correctly.

## Maintenance notes

- The encoded-string decision avoids a migration but adds a decode contract:
  any new period kind MUST go through `PeriodSpec`'s mapper, never raw
  `valueOf`.
- If a future plan needs per-period spec history, promote the column then
  (migration 24) — archived periods already store concrete dates, so this is
  a display-only concern.

## Escape hatches

- If period parsing is more widespread than the grep suggests (generated
  code, DataStore keys), extend the mapper — but if any site must keep raw
  enum semantics for compatibility, STOP and report the site list.
- If `BudgetPeriodManager`'s transition math proves inseparable from
  WorkManager/alarm scheduling timing, implement spec support only for
  display + manual period edits and report the scheduling limitation —
  a mis-scheduled payday period is worse than none.
