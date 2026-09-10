# Implementation Plans — MoneyPal audit round 1

Written by the `improve` skill against commit **`a2fda9c`** (full history:
`git rev-parse HEAD` should print `a2fda9cce433b5e661a2e52313bb638f93f5d751`).
If HEAD differs, re-run the audit/recon before executing these plans — excerpts
may have drifted.

## Scope and ground rules

- Executors: read the plan top-to-bottom before touching anything. Match repo
  conventions (see AGENTS.md / CONTRIBUTING.md). Only edit files listed under
  "In scope". Do not reformat unrelated code.
- Verification gates use flavor-qualified Gradle tasks — `:app` has no default
  flavor. The Gradle wrapper requires JDK 17–23 (it does not run on JDK 25).
- Do not modify files under `plans/` other than updating this index's status
  column and the plan headers you are executing.

## Recommended execution order

Dependency: **002 must land before any other recurring-expense change**; its
characterization tests are the safety net for the semantics fix. 003 and 001
are independent of each other. 004 can be done any time (pure test additions +
one tiny extraction). 005 is independent. 006/007 are trivial cleanups.

| Order | Plan | Summary | Depends on | Status |
|:------|:-----|:--------|:-----------|:-------|
| 1 | `001-backup-privacy.md` | Stop unencrypted cloud/device backup of the finance DB | — | DONE |
| 2 | `002-monthly-recurrence-semantics.md` | Unify month-end handling between "due today" and notification scheduler | — | DONE |
| 3 | `003-clock-injection.md` | Route period/time logic through `TimeProvider` so tests can freeze time | — | DONE |
| 4 | `004-bug-report-tests.md` | Unit-test zip entry-name sanitization (zip-slip guard) | — | DONE |
| 5 | `005-wear-sync-node-validation.md` | Validate Wear message source node before ingest/snapshot replies | — | DONE |
| 6 | `006-namespace-dup-cleanup.md` | Remove duplicated `namespace` assignment in `app/build.gradle.kts` | — | DONE |
| 7 | `007-readme-link-cleanup.md` | Remove/replace post-rebrand links that 404 until listings exist | — | DONE |

All round-1 plans executed on **2026-09-04** against `a2fda9c`. Deviations from plan text (all reported in the
session): plan 002 additionally unified a third duplicate "due today" predicate discovered in
`RecurrentExpenseNotificationWorker` (the scheduler's clamped notification was being suppressed by the
worker's exact-match check); plan 004 hardened `sanitizeZipEntryName` to collapse dot runs/trim leading
dots so bare `..` names can never form a traversal segment (the plan's own test invariants required it);
plan 003 left `RecurrentExpenseNotificationWorker` clock reads at the `doWork` boundary because it is a
WorkManager-constructed `CoroutineWorker` (not `@HiltWorker`), so a constructor param was not possible.
No receiver/worker unit tests were added (none existed; they'd require Android fakes) — the receivers'
logic was extracted into `internal suspend fun`s taking `today` instead.

## Round 2 — feature plans (2026-09-10, against `614cb49`)

User-selected product features. All are independent of each other except
where noted; recommended order below reflects risk (DB-touching last-ish)
and synergy.

| Order | Plan | Summary | Depends on | Status |
|:------|:-----|:--------|:-----------|:-------|
| 8 | `008-history-search-filters.md` | Search query + category/amount/recurrent/credit filters in History | — | DONE |
| 9 | `009-biometric-app-lock.md` | Fingerprint/device-credential gate via androidx.biometric, opt-in | — | DONE |
| 10 | `010-backup-restore.md` | Versioned local JSON backup incl. categories/occurrences + non-destructive restore | — | TODO |
| 11 | `011-category-envelopes.md` | Per-category budget limits (Room 17→18 auto-migration) + progress on Analytics | — | TODO |
| 12 | `012-savings-goals.md` | Track savings-goal progress from income entries + ETA on the savings card | — | TODO |
| 13 | `013-digest-and-shortcuts.md` | Opt-in Monday weekly digest (PeriodicWork) + static "Add expense" shortcut | — | TODO |

Round-2 status (2026-09-10): plans 008 and 009 executed against `b696f9b`. Deviations from
plan text (all reported in the session): plan 008 implemented filter state keyed by category
*name* rather than category id (chips toggle by name; uncategorized transactions are excluded
while a category filter is active), and the amount-range UI shipped as a
`HistoryAmountFilterSheet` ModalBottomSheet opened from a tune icon in the search bar rather
than inline fields; plan 009 ships the lock gate as `AppLockController` + `AppLockOverlay` in
`presentation/lock/`, with the overlay additionally swallowing back presses and full-screen
tap hit-testing so input can never reach the NavHost while locked, and the toggle refuses to
enable (Toast + no persist) when no biometric/device credential is enrolled.

Dependency notes:
- 008 and 009 are fully independent of everything — good first picks.
- 011 follows 010 in time so backup can be extended with envelope fields
  (follow-up noted inside 011's maintenance notes; not a hard dependency).
- 011 touches Room (schema 17→18) — run its migration gate before release.
- 013 reuses `BudgetStateCalculator` aggregates; no ordering constraint.

Correction vs. the original feature survey: **budget rollover was proposed
but already exists** (`rollOverEnabled/rollOverLimit/rollOverCarryForward`,
`RolloverDialog`, split-equally / carry-to-tomorrow / discard strategies,
unit + E2E tests). It was replaced by category envelopes (011) at user
choice.

## Considered and rejected (do not re-audit)

- **Internal `Minus*` identifiers retained** (`MinusApplication`,
  `MinusCsv*`, `Theme.Minus`, `minus_export.csv`, task group `"minus"`,
  `minus.includeWearModule`): deliberate scope decision from the rebrand —
  renaming is cosmetic and risks churn; not a bug.
- **Amounts stored as `String` in Room** (`TransactionEntity.amount`):
  verified good — money never touches a floating-point column.
- **CSV export to Downloads**: uses `MediaStore.Downloads`
  (`CsvTransferManager.kt`), correctly scoped for targetSdk 36 — not a bug.
- **Exported widget/QS-tile/boot receivers**: standard Android patterns with
  protected system broadcasts; not findings.

## What was audited / not audited

Audited: money math, monthly recurrence (calculator + scheduler), period
rollover/time logic, notification scheduling, manifests (phone + wear),
backup rules, CSV import worker + export path, bug-report zip generation,
Wear sync message flow. Not audited in depth: Compose rendering/screenshot
fidelity, per-DAO query performance, translation quality across locales,
WorkManager retry semantics, analytics aggregation math internals.
