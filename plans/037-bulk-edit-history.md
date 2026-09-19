# Plan 037 — Bulk edit in History (multi-select → change category / delete)

**Status:** TODO
**Written against commit:** `467ce9d` (2026-09-19) — round 5 (features)
**Category:** feature (small–medium)
**Depends on:** nothing
**Effort:** S–M · **Risk of fix:** low (no schema change)

> Provenance note: this plan replaces an earlier slate item ("scheduled
> encrypted backup") which was **rejected during vetting** — the app already
> ships it (plan 004: `AutoBackupScheduler`/`AutoBackupWorker`, SAF tree URI,
> 15-day cadence; see `Settings.kt` auto-backup rows and
> `presentation/notification/AutoBackupWorker.kt`). Do not re-plan that idea.

## Why

Users with imported history (plan 030 makes imports easy) or mis-categorized
weeks need to fix many transactions at once. Today every fix is a per-row
open-edit-save cycle through `TransactionEditScreen`. Multi-select with
"change category" and "delete" turns a 30-minute chore into 5 seconds.

## Conventions (every round-5 plan)

- User-facing strings in `app/src/main/res/values/strings.xml` **plus
  `values-es` and `values-fr`** (other locales are Crowdin-managed).
- Pure logic in `domain/` with JUnit4 + Truth tests where applicable.
- Conventional commits, one commit for this plan.
- Global verification gate:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

## Current state (verified)

- History list is driven by `HistoryViewModel.kt` (filtered display list at
  ~line 452 via `filterTransactions`) and `HistoryScreen.kt` (search bar at
  ~line 180). MVI intents live in `HistoryMviContract.kt` (e.g. `SetSearchQuery`
  line 12, `SetPaymentMethodFilter` line 17) — add selection intents there.
- Rows already support per-row actions (swipe components:
  `presentation/ui/theme/component/SwipeActions.kt` / `SwipeToDismiss.kt`);
  edit flow: `history/edit/TransactionEditScreen.kt`.
- DAO has single-row mutations (`TransactionDao.update`, `deleteById` at
  ~lines 80-88) but **no bulk variants** — they must be added.
- Category source for the picker: History already loads category names for
  filters (`HistoryFiltersTest` passes `categoryNames`; the VM fetches them).
- Bulk-insert precedent in DAO: `insertAllOrReplace` (~line 77) shows the
  list-parameter style.

## Steps

### Step 1 — DAO bulk operations

In `TransactionDao` add:

```kotlin
@Query("UPDATE transactions SET categoryId = :categoryId WHERE id IN (:ids)")
suspend fun updateCategoryForIds(ids: List<Long>, categoryId: Long?)

@Query("DELETE FROM transactions WHERE id IN (:ids)")
suspend fun deleteByIds(ids: List<Long>): Int
```

(`categoryId = null` clears the category.) Expose thin repository methods
(`BudgetRepository` + Impl). SQLite's `IN` parameter limit is 999 — chunk the
id list in the repository (500 per chunk) and run chunks in one
`withTransaction` if the DB class exposes it, else sequentially.

### Step 2 — MVI selection state

`HistoryMviContract`: add to UiState
`selection: Set<Long> = emptySet()` + `isSelectionMode: Boolean` (derived:
`selection.isNotEmpty()`); intents: `EnterSelectionMode(id)`,
`ToggleSelection(id)`, `SelectAllDisplayed`, `ClearSelection`,
`BulkChangeCategory(categoryId: Long?)`, `BulkDeleteSelected`.
`HistoryViewModel` implements: `Bulk*` intents call the repository, then
clear selection and let the existing flow re-emit. Recurring *templates*
(isRecurrent && not materialized) must be **excluded from selection** —
deleting/editing a template is a different flow; filter them out of
`SelectAllDisplayed` and reject their toggles.

### Step 3 — UI

- `HistoryScreen`: long-press on a row enters selection mode (row checkable,
  count in a top bar replacing the search bar, "Select all" overflow);
  while selecting, disable swipe actions (pass a flag into the row
  composable so `SwipeActions`/`SwipeToDismiss` short-circuit gestures).
- Bottom action bar when selecting: Change category (opens a sheet listing
  categories — reuse the existing category row component used in the editor's
  category toolbar, `editor/category/CategoryToolbar.kt` area), Delete
  (confirmation dialog with count; no undo in v1 — state that in the dialog),
  Cancel.
- Strings `bulk_edit_*` (select, selected-count plural, change category,
  delete confirm title/message, cancel) in all three locales — use a plural
  resource for the count (pattern: `R.plurals.days` usage in
  `BudgetPeriodSheet.kt` line 862).

## Out of scope

- Bulk amount/date editing, bulk merge, undo/trash.
- Selection across period boundaries (v1 selects within the currently
  displayed list only).
- Bulk-editing recurring templates or SMS-capture metadata.

## Test plan

- androidTest DAO test (pattern: `AppDatabaseMigrationTest`'s in-memory Room
  setup): `updateCategoryForIds` updates only targeted ids incl. null-clear;
  `deleteByIds` returns count and leaves others; >999 ids chunked path via
  repository test with a 1,200-row fixture (instrumented; skip with note if
  no device).
- ViewModel test (mockk + turbine, pattern: `ChangelogHistoryViewModelTest`):
  selection toggle/add/clear; template rows rejected; bulk intents call
  repository with the selection and clear it on success.

## Done criteria (machine-checkable)

1. `grep -n "updateCategoryForIds\|deleteByIds" app/src/main/java/com/sachit/moneypal/data/local/dao/TransactionDao.kt`
   — both present.
2. `./gradlew :app:testFossDebugUnitTest` — exit 0; compile gate — exit 0.
3. `grep -c "bulk_edit" app/src/main/res/values/strings.xml
   app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml`
   — ≥4 matches in each.
4. Manual: long-press → select 5 rows → change category → list re-renders
   with new category; select 3 → delete → confirm → gone; swipe gestures
   inert while selecting.

## Maintenance notes

- Selection state is deliberately ephemeral (process death clears it) — fine
  for v1; if users ask for persistence, move `selection` into a SavedStateHandle.
- If plan 031 (heatmap) lands first, its day-tap interaction must not fight
  long-press selection; both features touch `HistoryScreen` — coordinate.

## Escape hatches

- If rows are recycled across dates in a way that makes per-row checked state
  inconsistent, key rows by transaction id in the LazyColumn (`key =`) —
  report if keying is impossible.
- If the repository has no `withTransaction` support and chunked updates
  risk partial application, report — partial category updates are acceptable,
  partial deletes are NOT (must be all-or-nothing or clearly surfaced).
