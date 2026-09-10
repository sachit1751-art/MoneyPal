# Plan 008 — History search & filters

Written against commit **`614cb49`** (`git rev-parse --short HEAD` should print `614cb49`).
If HEAD differs, re-verify the excerpts below before executing.

## Why this matters

History is the only way to revisit past spending, and today it is scroll-only:
`HistoryViewModel` builds `displayTransactions` / `groupedCurrentTransactions` /
`groupedPastTransactions` with date grouping but has **no search query and no
filter state anywhere** in the MVI contract (`HistoryMviContract.kt` has no
`Search`/`Filter` intents). Once a user has a few months of data, finding "that
₹450 pharmacy purchase" is impossible. Search + category/amount filters is a
top-3 requested feature in budgeting apps and is low-risk: it does not touch
money math, period logic, or the database.

## Current state (verified excerpts)

`app/src/main/java/com/sachit/moneypal/presentation/ui/history/HistoryMviContract.kt`
— state is derived from a `combine(...)` of 7 flows in `HistoryViewModel.kt`
(lines ~85–120); the UI-intent sealed interface only covers expand/delete/edit/
credit actions:

```kotlin
sealed interface HistoryUiIntent {
    data class ToggleExpandedDate(val date: LocalDate) : HistoryUiIntent
    ...
    data class UpdateCreditCutoffDay(val day: Int) : HistoryUiIntent
}
```

`HistoryUiState` (same file) exposes the derived lists:

```kotlin
val displayTransactions: List<Transaction> = emptyList(),
val groupedCurrentTransactions: Map<LocalDate?, List<Transaction>> = emptyMap(),
val groupedPastTransactions: Map<LocalDate?, List<Transaction>> = emptyMap(),
```

`Transaction` domain model
(`app/src/main/java/com/sachit/moneypal/domain/model/TransactionModel.kt`) has
`comment: String`, `amount: BigDecimal`, `categoryId: Long?`,
`date: LocalDateTime?`, `isRecurrent`, `isCredit` — everything needed for
in-memory filtering.

`Category` (`domain/model/Category.kt`) is a plain data class (`id`, `name`,
`isHidden`, ...). History already receives `budgetTransactionHandler
.budgetRepository.getActiveCategories()` in its `combine` block, so category
names are available for filter chips.

## Design decisions

- **In-memory filtering, not SQL.** The VM already loads all period
  transactions into memory and groups them per day; filtering the derived lists
  keeps one code path and avoids new DAO surface. Fine for personal budgets
  (thousands of rows at most).
- **Filter predicate lives in a pure function** so it is unit-testable without
  Android, following the repo convention that date/budget logic stays out of UI
  (see AGENTS.md "Keep date/budget/recurrence logic out of UI").
- **MVI-compliant**: search/filter state goes through `HistoryUiIntent`, and the
  filter inputs join the existing `uiInputs` combine list in `HistoryViewModel`
  (the list-of-flows `combine` at lines ~65–83).
- **Strings in resources** (`values/strings.xml`, English source; Crowdin
  handles locales — do not hand-translate).

## In scope

- `app/src/main/java/com/sachit/moneypal/presentation/ui/history/HistoryMviContract.kt`
- `app/src/main/java/com/sachit/moneypal/presentation/ui/history/HistoryViewModel.kt`
- `app/src/main/java/com/sachit/moneypal/presentation/ui/history/HistoryCalculations.kt` (or a new sibling `HistoryFilters.kt` — prefer new file `HistoryFilters.kt`)
- New: `app/src/main/java/com/sachit/moneypal/presentation/ui/history/HistorySearchBar.kt`
- `app/src/main/java/com/sachit/moneypal/presentation/ui/history/HistoryScreen.kt` (wire the search bar)
- `app/src/main/res/values/strings.xml` (new English strings only)
- New tests: `app/src/test/java/com/sachit/moneypal/presentation/ui/history/HistoryFiltersTest.kt`

## Out of scope

- Any DAO/Room query changes, full-text search across *all* periods (only the
  currently rendered lists are searchable).
- Analytics screen filters.
- Wear app history.
- Changing `displayTransactions` semantics for anything but the new filter.

## Steps

1. **Create `HistoryFilters.kt`** next to `HistoryCalculations.kt` with:

   ```kotlin
   data class HistoryFilterState(
       val query: String = "",
       val categoryId: Long? = null,      // null = all categories
       val minAmount: BigDecimal? = null,
       val maxAmount: BigDecimal? = null,
       val recurrentOnly: Boolean = false,
       val creditOnly: Boolean = false,
   ) {
       val isActive: Boolean
           get() = query.isNotBlank() || categoryId != null || minAmount != null ||
               maxAmount != null || recurrentOnly || creditOnly
   }

   fun filterTransactions(
       transactions: List<Transaction>,
       filter: HistoryFilterState,
       categoryNames: Map<Long, String>,
   ): List<Transaction>
   ```

   Predicate rules (all case-insensitive, `Locale.ROOT` lowercase):
   - `query` matches if it is contained in `transaction.comment`, **or** in the
     category name of `transaction.categoryId` (via `categoryNames`), **or** in
     the plain-string of the amount (e.g. "450" matches 450.00).
   - `categoryId` matches exactly; a transaction with `categoryId = null` is
     excluded when a category filter is active.
   - `minAmount`/`maxAmount` compare against `transaction.amount.abs()` so
     income (negative amounts) is searchable too.
   - `recurrentOnly` requires `isRecurrent == true`; `creditOnly` requires
     `isCredit == true`.
   - Never match deleted or adjustmenet-free: filter operates on whatever list
     it is given (callers pass already-period-filtered lists — it must not
     re-filter `isDeleted` itself, because the pending-removed undo flow relies
     on keeping recently-removed items renderable).

2. **MVI wiring.** In `HistoryMviContract.kt` add:

   ```kotlin
   data class SetSearchQuery(val query: String) : HistoryUiIntent
   data class SetCategoryFilter(val categoryId: Long?) : HistoryUiIntent
   data class SetAmountFilter(val min: BigDecimal?, val max: BigDecimal?) : HistoryUiIntent
   data class ToggleRecurrentOnly(val enabled: Boolean) : HistoryUiIntent
   data class ToggleCreditOnly(val enabled: Boolean) : HistoryUiIntent
   data object ClearFilters : HistoryUiIntent
   ```

   Add `filter: HistoryFilterState = HistoryFilterState()` to `HistoryUiState`.
   Add a `MutableStateFlow<HistoryFilterState>` to `HistoryViewModel`, append it
   to the `uiInputs` combine list (index 11), map it through in the
   `UIInputs` data class, and in `calculateHistoryUiState` apply
   `filterTransactions` to `displayTransactions` **before** date grouping so
   grouped maps only contain matches. Intent handlers just `.update {}` the
   flow. `ClearFilters` resets to `HistoryFilterState()`.

3. **UI.** Create `HistorySearchBar.kt`: a Material 3 `SearchBar`-styled row
   (use `OutlinedTextField` with leading `Icons.Rounded.Search` and a trailing
   clear `IconButton` when `filter.isActive`) plus a horizontal row of Filter
   Chips: one per active category (top 8 by existing `tags` ordering — reuse
   the `tags: List<String>` already in `HistoryUiState` rather than adding a
   new category flow), plus "Recurrent" and "Credit" chips. Amount-range and
   full filter editing live in a `ModalBottomSheet` opened from a tune icon —
   model the sheet on `EditorDialogs.kt` styling (`BottomSheetWrapper` from
   `presentation/ui/theme/component/`). Insert `<HistorySearchBar>` at the top
   of `HistoryScreen.kt`'s LazyColumn `item {}` above the
   `BudgetDisplaySection`. When `filter.isActive`, show a small
   "N results" caption under the bar using a new string resource.

4. **Strings.** Add to `values/strings.xml` (exact names):
   `history_search_hint` ("Search transactions…"), `history_filter_results`
   ("%1$d results"), `history_filter_recurrent` ("Recurring"),
   `history_filter_credit` ("Credit"), `history_filter_clear` ("Clear"),
   `history_filter_amount_title` ("Amount range"), `history_filter_apply`
   ("Apply"). Do not touch other locale files.

5. **Tests.** `HistoryFiltersTest.kt` (JUnit4 + Truth, mirroring the style of
   `app/src/test/java/com/sachit/moneypal/domain/calculator/*`): cover query
   match on comment/category/amount, case-insensitivity, category filter,
   amount bounds (inclusive), combined predicates, and that an empty
   `HistoryFilterState` returns the input list unchanged (same instance is
   fine to assert via content equality). Build `Transaction` fixtures with
   `Transaction.create(...)`.

## Verification gates

```bash
./gradlew :app:compileFossDebugKotlin
./gradlew :app:testFossDebugUnitTest --tests "com.sachit.moneypal.presentation.ui.history.HistoryFiltersTest"
./gradlew :app:testFossDebugUnitTest
```

All three must pass. Existing History UI Paparazzi tests
(`app/src/test/**/history/**`) must be visually unchanged when no filter is
active — the search bar is a new item, so if a Paparazzi test renders the
full History screen it will need its golden image regenerated *only if* the
test actually includes the search bar node; check before regenerating, and
prefer scoping the search bar out of existing preview coverage by keeping new
Paparazzi tests in `HistorySearchBarTest.kt` instead.

## Done criteria

- [ ] Typing in the search box narrows both current and past-period grouped lists live.
- [ ] Category chip / amount range / recurrent / credit filters compose with the query.
- [ ] `Clear` resets to full list without recreating the screen.
- [ ] All verification gates green.

## Maintenance notes

- If a future plan moves History to Paging/SQL, `filterTransactions` is the
  seam to replace with a DAO query — keep it pure so that swap is mechanical.
- New settings must never be added to `UserSettings` for filter state; it is
  ephemeral UI state, not a user preference.

## Escape hatches — STOP and report if

- The `uiInputs` combine in `HistoryViewModel` has been refactored away from
  the list-of-flows shape (array indexing pattern) — the wiring instructions
  above would need re-deriving.
- `HistoryScreen.kt` no longer uses a LazyColumn with `BudgetDisplaySection`
  as an early item.
- Adding a 7th flow to the big `uiState` combine breaks the 5-flow `combine`
  overload silently (Kotlin resolves `combine` overloads by array — the VM
  already uses the array form; keep it).
