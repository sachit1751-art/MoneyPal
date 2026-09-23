# Plan 044 — Monthly spending report (shareable PDF)

**Status:** TODO
**Written against commit:** `48bb35b` (2026-09-23)
**Category:** feature (medium)
**Depends on:** plan 027 (BigDecimal totals in SQL, landed)
**Effort:** M · **Risk:** medium (new dependency + storage permission-free
file sharing)

## Why

MoneyPal already builds text summaries (`domain/report/SpendingReportBuilder.kt`)
and offers shareable reports for single entries, but there is no *period* report
a user can save, print, or send to a partner/accountant. A one-tap PDF for the
current or previous budget period uses only data the app already computes.

## Current state (verified)

- `SpendingReportBuilder.kt` exists in `domain/report/` (text reports).
- DAO returns raw `amount` rows (`TransactionDao.kt` lines 42/51 — the plan-027
  BigDecimal pattern); grouping happens in Kotlin.
- `BudgetCalculator.calculate(...)` (line 18) produces per-period figures and
  `calculatePeriodEnd` handles period math.
- Settings screen has a widgets sheet + share hooks — a "Monthly report"
  entry fits the existing export area.
- No PDF library in the version catalog; Android's built-in
  `android.graphics.pdf.PdfDocument` requires **no** dependency and no storage
  permission when written to the app's cache dir + shared via FileProvider.

## Steps

1. **Domain**: extend `domain/report/` with `MonthlyReportData` — a pure data
   holder built from existing repository queries: period label + dates, total
   spend, total income, per-category breakdown (name + BigDecimal total +
   share), top 5 expenses, income count, no-spend days count (reuse
   `NoSpendStreakCalculator` if it exposes day-level data; otherwise compute
   in the builder). Pure builder `MonthlyReportBuilder.build(...)`, fully
   unit-tested.
2. **DAO** (if missing): a `SELECT amount, comment, date, categoryId FROM
   transactions WHERE date >= :start AND date < :end AND is_deleted = 0`
   query returning a lightweight projection — follow the plan-027 style
   (amounts stay TEXT, never `CAST AS REAL`).
3. **PDF rendering**: `presentation/report/MonthlyReportPdfWriter.kt` using
   `android.graphics.pdf.PdfDocument` + `android.graphics.Canvas` text/paint
   only (no WebView, no extra dependency). A4 pages; simple typographic layout:
   header (period, generated date), totals block, category table, top
   expenses. Money formatting reuses the app's currency formatter (not
   `WidgetCurrencyFormatter` — that's widget-specific).
4. **Sharing**: write to `context.cacheDir/reports/`, share via the existing
   FileProvider config (verify `AndroidManifest.xml` has a FileProvider; if
   not, add one with `file_provider_paths` covering cache). `ACTION_SEND`
   with `application/pdf`.
5. **UI**: entry point in the export/settings area and a share icon on the
   Analytics period header: "Export period report (PDF)". Strings ×3 locales.
6. **Feature flag**: none needed; failure path = toast/report not written.

## Out of scope

- Charts inside the PDF (text tables only — keep rendering deterministic),
  multi-period reports, CSV/PDF of *all* history, wear.

## Test plan

- `MonthlyReportBuilderTest`: empty period, mixed income/spend, category
  share math sums to 100, top-5 ordering ties broken by date.
- `MonthlyReportPdfWriter`: not unit-testable (android.graphics) — cover the
  data assembly; verify by manual render on device + Paparazzi-free.
- ViewModel test: build → write → share intent carries correct URI/type.

## Done criteria

1. `grep -rn "PdfDocument" app/src/main --include="*.kt"` — single writer
   class.
2. Verification gate exit 0 including new builder tests.

## Escape hatches

- If per-category data requires joins the DAO doesn't expose cheaply, build
  categories from the same projection in Kotlin (already the repo pattern)
  instead of adding SQL.
- If the PDF layout grows past ~300 lines of Canvas code, STOP and propose a
  minimal layout library (e.g.Pdf-free HTML→print path) instead.
