# Plan 008 — Audit round 2 (pre-feature sweep)

Written against the working tree on **2026-09-08** (round-2 audit, run before the
onboarding-tutorial + bank-SMS-capture features). Round-1 plans (001–007) are DONE;
this file covers the findings from the second sweep of the money-math, migration,
CSV, sync, and scheduler code plus repo hygiene.

## Findings

| # | Finding | Category | Impact | Effort | Risk | Status |
|:--|:--------|:---------|:-------|:-------|:-----|:-------|
| 1 | `auto_push_safe_fast.sh` loops forever appending to `activity_log.txt`, committing random fake conventional-commit messages, and pushing to `main` every 15s. Pollutes history; can push unintended state. | Repo hygiene | High | S | Low | FIXED (script + log deleted) |
| 2 | Wear expense ingest (`app/src/wear/.../WearExpenseIngestor.kt`) always inserts into `transactions`, even when `today > settings.getPeriodEndDate()`; manual entries go to `queued_transactions` in that case (`BudgetTransactionHandler.applyTransaction`). Watch expenses past period end silently land in periodId 0 and are dropped from the next period. | Correctness | Medium | M | Medium | TODO (spec below) |
| 3 | `SettingsRepositoryImpl.resetTutorials()` resets `TUTORIAL_BOX_COMPLETED` and `ANALYTICS_TUTORIAL_COMPLETED` but not `ANALYTICS_SPENDS_TUTORIAL_COMPLETED`. | Bug | Low | S | Low | FIXED |
| 4 | `showPastTransactions` DataStore read-default is `true` (`SettingsRepositoryImpl` line ~157) while `UserSettings.showPastTransactions` defaults `false`. UI default is `true`. Fresh-install behavior depends on which path constructs `UserSettings`. | Bug (inconsistent default) | Low | S | Low | FIXED (model default aligned to `true`) |
| 5 | `BudgetRepositoryImpl.Transaction.toEntity()` uses `date!!` on a nullable field (`TransactionModel.kt` declares `date: LocalDateTime?`). All current call sites set it, so latent — but any future null-date transaction crashes the data layer. | Tech debt | Low | S | Low | FIXED (defensive fallback derived from `createdAt`) |

Rejected this round (do not re-audit): `BudgetCalculator` vs
`BudgetStateCalculator` duplication of split/allocation math is real but
`BudgetStateCalculator` is the live path (ViewModel uses it); unifying is
refactor churn with screenshot-test blast radius — not worth doing now.
`TransactionPeriodMapper` BIWEEKLY calendar bucketing uses ISO week parity —
by-design approximation, documented behavior.

## Spec for remaining TODO — Wear ingest period-end queueing

**Goal:** mirror the manual-entry rule in `WearExpenseIngestor.ingest`.

1. In `WearExpenseIngestor`, inject `SettingsRepository` (Hilt, wear flavor has
   the same `@Singleton` graph via `:sync-contract`-free classes).
2. After the duplicate pre-check and amount validation:
   - `val settings = budgetRepository.getBudgetSettingsSync()`
   - `val today = LocalDate.now()`
   - if `settings != null && today.isAfter(settings.getPeriodEndDate())`:
     build the same `Transaction` and call `repository.addQueuedTransaction(tx)`
     instead of `addTransactionIfAbsent`. Log `"ingest: queued for next period"`.
   - else: unchanged `addTransactionIfAbsent` path (DB-level dedupe via
     `clientGeneratedId` stays).
3. Known limitation (accept): `queued_transactions` has no `clientGeneratedId`
   column, so a crash between queue-insert and ACK can duplicate a queued row on
   redelivery. Pre-check with `existsTransactionByClientGeneratedId` still guards
   the common case. Do NOT migrate the schema for this.
4. Tests: none exist for the wear source set; verify via
   `:app:compileWearDebugKotlin` and manual pairing. Escalation: if the
   `wear` source set cannot resolve `SettingsRepository` (flavor graph change),
   STOP and report instead of restructuring DI.

Verification gate: `./gradlew :app:compileWearDebugKotlin :app:compileFossDebugKotlin`.
