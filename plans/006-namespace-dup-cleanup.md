# 006 — Remove the duplicated `namespace` assignment in `app/build.gradle.kts`

**Audit base:** commit `a2fda9c` — verify with `git rev-parse HEAD` first.

## Why this matters

`app/build.gradle.kts` assigns `namespace` twice inside the `android {}`
block. Both assignments set the same value today (`com.sachit.moneypal`), so
there is no functional bug — but the second assignment silently wins and the
duplication is confusing for anyone editing the file (a future edit changing
only the first would appear to do nothing). Trivial, safe cleanup.

## Current state

Find both occurrences:

```bash
grep -n "namespace = " app/build.gradle.kts
```

Expected: two hits — one near the top of the `android {}` block (right after
`compileSdk { ... }`, currently ~line 73) and one further down just after the
`packaging { ... }` block (~line 197):

```kotlin
    namespace = "com.sachit.moneypal"
```

(the second is the tail-end duplicate, ~line 197, preceded by the
`packaging { resources.excludes += ... }` block and followed by
`dependenciesInfo { ... }`).

## Steps

1. Run the grep above; confirm exactly two hits with identical values
   `com.sachit.moneypal`. If values differ, STOP and report (that would be a
   real bug, not a duplicate).
2. Delete the **second** occurrence (the one after the `packaging { ... }`
   block), keeping the first (near `compileSdk`). Keep surrounding
   whitespace/ordering intact.
3. Verify a single assignment remains: `grep -c "namespace = " app/build.gradle.kts` → `1`.

## Done criteria

- [ ] `grep -c "namespace = " app/build.gradle.kts` prints `1`
- [ ] `./gradlew :app:compileFossDebugKotlin` → `BUILD SUCCESSFUL` (proves the
      namespace is still resolved; no manifest/R8 implications)

## Test plan

None — Gradle script cleanup; the compile gate is the verification.

## Maintenance note

- When moving build blocks around, keep exactly one `namespace` assignment in
  the `android {}` block (convention: near the top with `compileSdk`).
- The wear module (`wear/build.gradle.kts`) has a single assignment — leave it.

## Out of scope / boundaries

- No other build-file changes (do not touch the duplicated dependency
  aliases like `foundation.old` — that is a separate migration concern).
