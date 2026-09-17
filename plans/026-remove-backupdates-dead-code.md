# Plan 026 — Delete dead BackupDates/backupString helpers

**Status:** TODO
**Written against commit:** `664cdc1` (2026-09-17)
**Category:** chore (dead code)
**Depends on:** nothing
**Effort:** S · **Risk of fix:** none

## Why

`app/src/main/java/com/sachit/moneypal/data/backup/BackupModels.kt` ships two
internal helper blocks that have **zero usages** anywhere in the repo (verified
at `664cdc1` with
`grep -rn "BackupDates\|backupString" app/src --include="*.kt"` — the only
hits are their own definitions):

```kotlin
/** Converts ISO-8601 dates without pulling in kotlinx-datetime. */
internal object BackupDates {
    fun toEpochMillis(date: LocalDateTime): Long =
        date.toEpochSecond(ZoneOffset.UTC) * 1000
    fun fromEpochMillis(millis: Long): LocalDateTime =
        LocalDateTime.ofEpochSecond(millis / 1000, 0, ZoneOffset.UTC)
    fun localDateToMillis(date: LocalDate): Long = date.toEpochDay() * 86400000
    fun localDateFromMillis(millis: Long): LocalDate =
        LocalDate.ofEpochDay(millis / 86400000)
}

/** String form of a BigDecimal for backup storage. */
internal fun BigDecimal?.backupString(): String? = this?.toPlainString()
```

They are leftovers from an earlier draft of the plan-010 backup layer; the
shipping code (`CreateBackupUseCase`, `RestoreBackupUseCase`, entity mappers)
does its own conversions inline. Dead code in a security-adjacent package
invites accidental use with subtly different semantics (e.g. `BackupDates`
uses UTC wall-clock encoding — correct here — but a future caller might assume
local time).

## Current state

- Definitions: `BackupModels.kt` bottom of file (~lines 160-177).
- `BackupModels.kt` has no dedicated test file (verified:
  `find app/src/test -iname "*backupmodels*"` — none); the codec/encryption
  tests don't touch these helpers.

## Steps

1. Delete the `internal object BackupDates { ... }` block and the
   `internal fun BigDecimal?.backupString()` extension from `BackupModels.kt`.
2. Remove imports that become unused (`java.time.LocalDate`,
   `java.time.LocalDateTime`, `java.time.Instant`, `java.time.ZoneOffset`,
   `java.math.BigDecimal`) — check each first; the data classes in the file
   use only primitives/Strings (verified), so all five should go.
3. Run the compile gate + full unit suite.

## Out of scope

- Any change to the serialized JSON format (schema v1 stays byte-identical).
- Refactoring the inline conversion code in `CreateBackupUseCase`/
  `RestoreBackupUseCase` — that duplication is live code and NOT in scope;
  flag it, don't touch it.

## Test plan

None needed — pure deletion verified by compile + suite.

## Done criteria (machine-checkable)

1. `grep -rn "BackupDates\|backupString" app/src --include="*.kt"` — 0 matches.
2. `./gradlew :app:compileFossDebugKotlin :sync-contract:compileKotlin` — exit 0.
3. `./gradlew :app:testFossDebugUnitTest` — exit 0.

## Maintenance notes

- If a future plan needs epoch-millis ↔ LocalDate conversions in the backup
  package, reintroduce ONE shared helper consciously (with tests), not two
  divergent copies.

## Escape hatches

- If the grep shows a usage the audit missed (reflection, generated source),
  STOP and report.
