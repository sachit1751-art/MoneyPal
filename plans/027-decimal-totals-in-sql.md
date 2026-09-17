# Plan 027 — Spend totals: sum BigDecimal, not SUM(CAST AS REAL)

**Status:** TODO
**Written against commit:** `664cdc1` (2026-09-17)
**Category:** fix (precision / money-handling rule)
**Depends on:** nothing
**Effort:** S · **Risk of fix:** low

## Why

The two SQL aggregate queries that produce "spent today / spent this period"
convert the TEXT money column to a binary float and sum it in SQLite:

`app/src/main/java/com/sachit/moneypal/data/local/dao/TransactionDao.kt`:
```sql
SELECT SUM(CAST(amount AS REAL)) FROM transactions WHERE date >= :startOfDay AND date < :endOfDay
SELECT SUM(CAST(amount AS REAL)) FROM transactions WHERE date >= :startDate AND date < :endDate
```

Two concrete problems:

1. **Repo rule violation.** AGENTS.md: money is `BigDecimal`/plain strings,
   never floats/`Double`. The sums come back as `Double?` and
   `BudgetRepositoryImpl` wraps them: `BigDecimal(it)`. Float-sum drift on the
   "spent so far" number is exactly what the rule exists to prevent, and for a
   budget app a cent of drift is user-visible.
2. **Silent total corruption on bad rows.** If any `amount` text fails to parse
   as a number (corrupted row, manual sqlite edit, old import bug), SQLite's
   `CAST` yields `0.0` for that row and the sum quietly EXCLUDES money the user
   spent. `BigDecimal(String)` would throw — a loud failure beats a silent
   wrong total.

The queries' only consumers are `getSpentForDate`/`getSpentForPeriod` in
`BudgetRepositoryImpl` (verified: no other callers). The heavy aggregation
paths (envelopes, burn rate, history) already aggregate `BigDecimal` in Kotlin
from full row lists — this plan aligns the last two SQL sums with that.

## Current state (verified excerpts)

`app/src/main/java/com/sachit/moneypal/data/local/dao/TransactionDao.kt`:
```kotlin
@Query("""
    SELECT SUM(CAST(amount AS REAL)) FROM transactions 
    WHERE date >= :startOfDay AND date < :endOfDay
""")
fun getTotalSpentForDay(startOfDay: Long, endOfDay: Long): Flow<Double?>

@Query("""
    SELECT SUM(CAST(amount AS REAL)) FROM transactions 
    WHERE date >= :startDate AND date < :endDate
""")
fun getTotalSpentForPeriod(startDate: Long, endDate: Long): Flow<Double?>
```

`app/src/main/java/com/sachit/moneypal/data/repository/BudgetRepositoryImpl.kt` (lines ~318-332):
```kotlin
override fun getSpentForDate(date: LocalDate): Flow<BigDecimal> {
    val startOfDay = date.toEpochDay() * 86400000
    val endOfDay = (date.plusDays(1)).toEpochDay() * 86400000
    return transactionDao.getTotalSpentForDay(startOfDay, endOfDay)
        .map { it?.let { BigDecimal(it) } ?: BigDecimal.ZERO }
}

override fun getSpentForPeriod(start: LocalDate, end: LocalDate): Flow<BigDecimal> {
    val startMillis = start.toEpochDay() * 86400000
    val endMillis = end.toEpochDay() * 86400000
    return transactionDao.getTotalSpentForPeriod(startMillis, endMillis)
        .map { it?.let { BigDecimal(it) } ?: BigDecimal.ZERO }
}
```

## Steps

### Step 1 — DAO returns raw amount rows; delete the float sums

Replace the two aggregate queries with single-column selections:

```kotlin
@Query("""
    SELECT amount FROM transactions 
    WHERE date >= :startOfDay AND date < :endOfDay
""")
fun getAmountsForDay(startOfDay: Long, endOfDay: Long): Flow<List<String>>

@Query("""
    SELECT amount FROM transactions 
    WHERE date >= :startDate AND date < :endDate
""")
fun getAmountsForPeriod(startDate: Long, endDate: Long): Flow<List<String>>
```

Then delete `getTotalSpentForDay`/`getTotalSpentForPeriod`.

### Step 2 — Repository sums with BigDecimal

```kotlin
override fun getSpentForDate(date: LocalDate): Flow<BigDecimal> {
    val startOfDay = date.toEpochDay() * 86400000
    val endOfDay = (date.plusDays(1)).toEpochDay() * 86400000
    return transactionDao.getAmountsForDay(startOfDay, endOfDay)
        .map { amounts -> sumAmounts(amounts) }
}

override fun getSpentForPeriod(start: LocalDate, end: LocalDate): Flow<BigDecimal> {
    val startMillis = start.toEpochDay() * 86400000
    val endMillis = end.toEpochDay() * 86400000
    return transactionDao.getAmountsForPeriod(startMillis, endMillis)
        .map { amounts -> sumAmounts(amounts) }
}
```

with the summer extracted as an `internal` top-level function in
`BudgetRepositoryImpl.kt` so a JVM test can reach it:

```kotlin
internal fun sumAmounts(amounts: List<String>): BigDecimal =
    amounts.fold(BigDecimal.ZERO) { acc, raw -> acc.add(BigDecimal(raw)) }
```

Deliberate semantics: `BigDecimal(raw)` THROWS on an unparseable row. That is
the wanted loud failure (the float version silently dropped the row). Do NOT
wrap in runCatching; do NOT filter bad rows. If a crash report ever lands
here, it is surfacing real data corruption — the fix then is a data repair,
not a swallow. `add` keeps the max scale of operands (no rounding), so sums of
2-dp amounts stay exact; no `setScale` needed.

### Step 3 — Test

`app/src/test/java/com/sachit/moneypal/data/repository/BudgetRepositorySumTest.kt`
(new; the `internal` function is same-module visible):

- empty list → `0`
- `["10.50", "2.25"]` → `12.75` exact (`compareTo == 0`)
- `["0.1", "0.2"]` → `0.3` exact (the case floats get wrong)
- `"999999999999.99"` accumulates without drift
- unparseable `"abc"` → throws `NumberFormatException` (assert throws)

Optional (only if the emulator gate runs): extend
`app/src/androidTest/java/com/sachit/moneypal/data/repository/BudgetRepositoryImplTest.kt`
with one round-trip: insert `0.1` + `0.2`, assert
`getSpentForPeriod(...).first()` equals `BigDecimal("0.3")`.

## Out of scope

- Every other aggregation (`BudgetCalculator`, `EnvelopeCalculator`,
  `HistoryCalculations`, burn rate, analytics) — already Kotlin/BigDecimal.
- The `findDuplicate` DAO query's `CAST(amount AS REAL) = :amount` — plan 024
  owns that.
- Any Room schema change — none; queries only, no migration.

## Test plan

See Step 3: JVM unit test for the summer; optional instrumented round-trip.

## Done criteria (machine-checkable)

1. `grep -n "CAST(amount AS REAL" app/src/main/java/com/sachit/moneypal/data/local/dao/TransactionDao.kt` — exactly 1 match left (the `findDuplicate` one, owned by plan 024), down from 3.
2. `grep -rn "getTotalSpentFor" app/src/main/java --include="*.kt"` — 0 matches.
3. `./gradlew :app:compileFossDebugKotlin` — exit 0.
4. `./gradlew :app:testFossDebugUnitTest` — exit 0 including `BudgetRepositorySumTest`.

## Maintenance notes

- Need a new aggregate? Follow this pattern: fetch rows/columns, sum in Kotlin
  with BigDecimal. Do not reintroduce SQL CAST sums.
- If the transactions table ever grows to tens of thousands of rows and
  per-period queries matter, add an index on `date` — performance was NOT a
  criterion in this change (rows per period are small).

## Escape hatches

- If Room cannot map a `Flow<List<String>>` single-column query (it can),
  STOP and report rather than switching to full-entity mapping hacks.
- If tests reveal amounts stored with currency symbols/commas in real device
  data (the CSV path strips them; SMS uses plain strings), STOP and report —
  that would be a separate data-repair finding.
