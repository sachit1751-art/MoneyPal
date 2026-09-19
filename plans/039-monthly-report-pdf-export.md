# Plan 039 — Monthly report: PDF export and share

**Status:** TODO
**Written against commit:** `467ce9d` (2026-09-19) — round 5 (features)
**Category:** feature (medium)
**Depends on:** nothing (independent; benefits from plan 027 landing first so
all spend totals are BigDecimal-summed)
**Effort:** M · **Risk of fix:** low

## Why

Users want to share a period summary — with a partner, an accountant, or their
own archive. The app already computes every number that belongs in such a
report (per-category totals, daily spend, budget vs actual, savings progress)
but it can only be seen on-screen or exported as raw CSV. A one-tap PDF share
completes the loop without adding any new computation: the report is a
**rendering of existing domain outputs**.

Android's built-in `PdfDocument` API covers this with zero new dependencies —
important for the FOSS flavor.

## Conventions (every round-5 plan)

- User-facing strings in `app/src/main/res/values/strings.xml` **plus
  `values-es` and `values-fr` copies** (other locales are Crowdin-managed).
- Money = `BigDecimal`/plain strings; format via the existing formatters in
  `presentation/util/font/format/NumberFormat.kt`
  (`formatCurrency(amount, currencyCode)`) — never `String.format` money.
- Pure logic in `domain/` with JUnit4 + Truth tests.
- Conventional commits, one commit for this plan.
- Global verification gate:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

## Current state (verified)

- Analytics ViewModel already produces the inputs:
  `AnalyticsViewModel.kt` — `filterTransactions` (line 361),
  `envelopeProgress` (line 441), `noSpendStreak` (line 437),
  `savingsGoalProgress` (line 445), previous-period comparison
  (`findPreviousPeriodTransactions`, line 475). Read the full `combine()`
  block (~lines 150-480) before wiring; the report must reuse the **same
  aggregator calls**, not duplicate math.
- `BudgetPeriod` enum + period date ranges:
  `domain/model/BudgetPeriodModel.kt` (line 11).
- CSV export precedent for file handling + share:
  `data/csv/` (`MinusCsvService` export side) and the transfer manager in
  `presentation/ui/settings/csv/CsvTransferManager.kt` — mirror how it gets a
  writable destination and kicks a share intent. PDF generation must NOT run
  on the main thread.
- Currency: `BudgetSettings.currencyCode` exists on the budget entity
  (schema 23) — the report uses the **period's** currency.

## Steps

### Step 1 — Report data model (domain)

New `app/src/main/java/com/sachit/moneypal/domain/report/MonthlyReportData.kt`:
a plain data class holding what the renderer needs — period label + date
range, currency code, total budget, total spent (BigDecimal), remaining,
per-category breakdown (name, amount, share-of-total as BigDecimal percent),
largest single expense, income total, savings-goal line (nullable). Provide a
pure `MonthlyReportBuilder` that takes the already-computed collections
(transactions, categories, envelope progress, savings progress) and returns
the report data. Unit-test the builder (share-of-total math, empty-history
edge, income exclusion).

### Step 2 — PDF renderer (data or presentation layer, no new deps)

New `app/src/main/java/com/sachit/moneypal/data/report/PdfReportWriter.kt`:

- `suspend fun write(context: Context, data: MonthlyReportData, out: OutputStream)`
  using `android.graphics.pdf.PdfDocument` + `Canvas` text drawing. A4 page,
  simple header (app name + period label), a totals block, a category table
  (name, amount, percent), footer with generation date.
- Text scale: use `TypedValue.applyDimension` with `TypedValue.COMPLEX_UNIT_SP`
  so system font scale doesn't clip text.
- No third-party PDF library; if drawing a table with Canvas proves too
  fiddly, a one-column-per-row layout is acceptable for v1 (report back which
  was chosen).
- Write to app cache first (`context.cacheDir/reports/`), then the UI layer
  offers FileProvider share (check `AndroidManifest.xml` for an existing
  FileProvider — if absent, add one scoped to cache-path with
  `androidx.core.content.FileProvider`; follow the manifest's existing
  provider entries pattern).

### Step 3 — UI entry point

In the Analytics screen (`presentation/ui/analytics/Analytics.kt`): an
overflow/menu action "Export PDF report" on the period selector. Flow: build
`MonthlyReportData` from the same state the screen already holds → show
progress → `PdfReportWriter` → share sheet (`ACTION_SEND`, `application/pdf`,
`FLAG_GRANT_READ_URI_PERMISSION`) with subject "MoneyPal report <period
label>". New strings `report_export_*` in all three locales.

## Out of scope

- Charts inside the PDF (text/table only for v1).
- Customizing report contents or date ranges beyond the current period.
- Email/Drive direct integrations — the system share sheet is the transport.
- Wear module.

## Test plan

- `MonthlyReportBuilderTest.kt` in
  `app/src/test/java/com/sachit/moneypal/domain/report/` (JUnit4 + Truth):
  percent-of-total sums to 100 (±0.01), income excluded from spend, empty
  transaction list yields a valid report with zero totals, single-category
  edge.
- `PdfReportWriter` is Android-dependent; cover it with one instrumented test
  IF a device is available (assert file exists, size > 0, starts with
  `%PDF`); otherwise note skipped in the commit message.
- Manual: generate for a period with 3+ categories; open the PDF on device;
  share to Gmail and verify preview.

## Done criteria (machine-checkable)

1. `./gradlew :app:testFossDebugUnitTest` — exit 0 including
   `MonthlyReportBuilderTest`.
2. Compile gate — exit 0.
3. `grep -rn "PdfDocument" app/src/main --include="*.kt"` — exactly the new
   writer file.
4. `grep -c "report_export" app/src/main/res/values/strings.xml
   app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml`
   — ≥3 matches in each.

## Maintenance notes

- The report builder is the stable seam: future "year in review" (plan 037)
  and CSV enhancements can reuse it. Keep `MonthlyReportData` free of
  Compose/Android imports so both phone and any future desktop export can use
  it.
- If plan 027 (decimal totals in SQL) hasn't landed, ensure the builder still
  receives Kotlin-side BigDecimal sums (Analytics already computes these in
  Kotlin — verify which path feeds it before wiring).

## Escape hatches

- If `AnalyticsViewModel`'s state does not expose everything needed (e.g. it
  discards categories for past periods), STOP and report which aggregate is
  missing — extend the ViewModel minimally rather than re-querying the DB from
  the writer.
- If FileProvider manifest work collides with an existing authority, report
  the conflict instead of picking a new authority string silently.
