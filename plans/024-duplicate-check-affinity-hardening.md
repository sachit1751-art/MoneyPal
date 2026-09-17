# Plan 024 — Duplicate check: exact + REAL belt-and-braces

**Status:** TODO
**Written against commit:** `664cdc1` (2026-09-17)
**Category:** fix (hardening — latent wrong-result bug)
**Depends on:** nothing
**Effort:** S · **Risk of fix:** low

## Why

The pre-save duplicate warning compares money like this:

`app/src/main/java/com/sachit/moneypal/data/local/dao/TransactionDao.kt`:
```sql
SELECT * FROM transactions
WHERE CAST(amount AS REAL) = :amount
    AND comment = :comment
    AND date >= :startOfDay AND date < :endOfDay
ORDER BY date DESC LIMIT 1
```
with `:amount` bound as a Kotlin `Double`
(`BudgetRepositoryImpl.findDuplicateTransaction`: `amount = amount.toDouble()`).

Two failure modes, both real SQLite semantics:

1. **Affinity makes `CAST(amount AS REAL) = :amount` fragile in both
   directions.** The `amount` column has TEXT affinity (entity:
   `val amount: String`). When the bind value's text representation doesn't
   round-trip the float exactly (`50.0` vs `"50"`, `0.1+0.2` artifacts), the
   comparison silently misses rows that are the same amount. TEXT-vs-REAL
   affinity coercion rules decide the result — not arithmetic.
2. **Cross-binary float equality** is exact-equality on IEEE doubles; any
   precision difference between how the stored text was written and how the
   parameter is rendered drops the match.

Symptom today: the duplicate warning sometimes simply doesn't fire. The
`REAL` cast is also an AGENTS.md money-rule violation in spirit (money→float).

This is **hardening**, not a confirmed user report — but the fix is small and
the query is one of three float-money sites (plan 027 removes the other two).

## Current state (verified excerpts)

`app/src/main/java/com/sachit/moneypal/data/local/dao/TransactionDao.kt`:
```kotlin
@Query("""
    SELECT * FROM transactions
    WHERE CAST(amount AS REAL) = :amount
        AND comment = :comment
        AND date >= :startOfDay AND date < :endOfDay
    ORDER BY date DESC LIMIT 1
""")
suspend fun findDuplicate(amount: Double, comment: String, startOfDay: Long, endOfDay: Long): TransactionEntity?
```

`app/src/main/java/com/sachit/moneypal/data/repository/BudgetRepositoryImpl.kt` (lines ~420-433):
```kotlin
override suspend fun findDuplicateTransaction(
    amount: BigDecimal,
    comment: String,
    day: LocalDate,
): Transaction? {
    val startOfDay = day.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
    val endOfDay = day.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
    return transactionDao.findDuplicate(
        amount = amount.toDouble(),
        comment = comment,
        startOfDay = startOfDay,
        endOfDay = endOfDay,
    )?.toDomain()
}
```

Caller: `BudgetTransactionHandler.kt:109` —
`budgetRepository.findDuplicateTransaction(amount, comment.trim(), today)`
(amount is the parsed BigDecimal entry; used to show a duplicate warning).
Storage format note: amounts are stored as plain strings
(`toPlainString()` in `Transaction.toEntity()`), so `"50"` and `"50.00"` can
both exist depending on entry path — the match must be NUMERIC, not textual.

## Steps

### Step 1 — DAO: numeric-normalize comparison, BigDecimal parameter

```kotlin
/**
 * Heuristic duplicate lookup: same numeric amount and comment within one
 * day. Compares stored TEXT amounts numerically by normalizing both sides
 * to a canonical decimal string (CAST to REAL, then TRIM zeros) — matching
 * 50, 50.0 and 50.00 while never doing money math in float.
 */
@Query("""
    SELECT * FROM transactions
    WHERE comment = :comment
        AND date >= :startOfDay AND date < :endOfDay
        AND REPLACE(RTRIM(RTRIM(CAST(amount AS REAL), '0'), '.'), '') =
            REPLACE(RTRIM(RTRIM(:amountReal, '0'), '.'), '')
    ORDER BY date DESC LIMIT 1
""")
suspend fun findDuplicate(
    amountReal: Double,
    comment: String,
    startOfDay: Long,
    endOfDay: Long,
): TransactionEntity?
```

Wait — that still puts float equality in the WHERE. The chosen design is the
**two-pass approach** (Step 2) instead; the single-SQL normalization above is
documented here only as the rejected alternative (RTRIM string games on REAL
text are SQLite-version-sensitive and hard to unit-test on JVM). Use Step 2.

### Step 2 — Two-pass: SQL narrows by (comment, day), Kotlin decides numerically

DAO — drop the amount condition entirely, keep it indexed and narrow:
```kotlin
/** Same comment within one day, newest first; amount equality decided by the caller. */
@Query("""
    SELECT * FROM transactions
    WHERE comment = :comment
        AND date >= :startOfDay AND date < :endOfDay
    ORDER BY date DESC LIMIT 50
""")
suspend fun findByCommentAndDay(
    comment: String,
    startOfDay: Long,
    endOfDay: Long,
): List<TransactionEntity>
```

Repository — numeric compare with BigDecimal:
```kotlin
override suspend fun findDuplicateTransaction(
    amount: BigDecimal,
    comment: String,
    day: LocalDate,
): Transaction? {
    val startOfDay = day.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
    val endOfDay = day.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
    val candidates = transactionDao.findByCommentAndDay(comment, startOfDay, endOfDay)
    return candidates.firstOrNull { entity ->
        runCatching { BigDecimal(entity.amount) }
            .getOrNull()
            ?.compareTo(amount) == 0
    }?.toDomain()
}
```

`compareTo` (not `equals`) so `50.00` matches `50`. A corrupted amount row is
skipped by `getOrNull()` rather than crashing the save flow — correct here:
this is a heuristic warning, not money math (contrast plan 027's deliberate
throw-on-garbage for totals). LIMIT 50 bounds the scan; comments within a day
rarely exceed a handful.

### Step 3 — Test

`app/src/test/java/com/sachit/moneypal/data/repository/BudgetRepositoryImplTest.kt`
does not exist on JVM (the instrumented one lives in androidTest). Create
`app/src/test/java/com/sachit/moneypal/presentation/ui/budget/DuplicateCheckMatchingTest.kt`
— a small pure test of the matching predicate by extracting it:

```kotlin
internal fun BigDecimal.matchesStoredAmount(raw: String?): Boolean =
    raw != null && runCatching { BigDecimal(raw) }.getOrNull()?.compareTo(this) == 0
```

(top-level `internal` in `BudgetRepositoryImpl.kt`). Cases:

- `BigDecimal("50")` matches `"50"`, `"50.0"`, `"50.00"`, `"5E+1"`
- `BigDecimal("0.1")` does NOT match `"0.1000000000000000055511151231257827"`-style drift (string preserved exactly, so this stays a non-match — assert `"0.1"` ≠ `"0.11"`)
- `"abc"` → no match, no throw
- `null`/blank → no match

If an instrumented round-trip is cheap, extend
`app/src/androidTest/java/com/sachit/moneypal/data/repository/BudgetRepositoryImplTest.kt`:
insert `"50.00"` row, call `findDuplicateTransaction(BigDecimal("50"), ...)`,
expect a hit.

## Out of scope

- `BudgetTransactionHandler` UI flow (warning dialog) — unchanged.
- Plan 027's totals queries.
- Any schema/index change.

## Test plan

See Step 3. Predicate unit test mandatory; instrumented test optional.

## Done criteria (machine-checkable)

1. `grep -n "CAST(amount AS REAL" app/src/main/java/com/sachit/moneypal/data/local/dao/TransactionDao.kt` — 0 matches (after 023's train: this was the last one).
2. `grep -n "toDouble()" app/src/main/java/com/sachit/moneypal/data/repository/BudgetRepositoryImpl.kt` — 0 matches.
3. `./gradlew :app:compileFossDebugKotlin` — exit 0.
4. `./gradlew :app:testFossDebugUnitTest` — exit 0 including the new predicate tests.

## Maintenance notes

- If duplicate detection ever needs amount-only matching (blank comments),
  keep the same two-pass shape (SQL narrows, Kotlin compares BigDecimal) —
  do not reintroduce float SQL equality.
- LIMIT 50 assumes comment-scoped candidates are few; if a future feature
  matches on high-frequency comments, add amount buckets to SQL consciously.

## Escape hatches

- If `BudgetTransactionHandler`'s caller passes a comment that can differ in
  whitespace from the stored one (today it passes `comment.trim()`, stored
  comments come from the same trim path), verify with a quick read; if they
  can diverge, STOP and report — matching needs a comment-normalization
  decision first.
