# Plan 030 — CSV import for foreign formats (column mapping + validation)

**Status:** TODO
**Written against commit:** `467ce9d` (2026-09-19) — round 5 (features)
**Category:** feature (medium)
**Depends on:** nothing (round-4 plan 029 touches `CsvImportWorker` — land it
first to avoid merge friction)
**Effort:** M · **Risk of fix:** low (new code path; the existing MoneyPal-format
import is untouched)

## Why

MoneyPal already exports and re-imports **its own** CSV format
(`data/csv/MinusCsvService.kt`, `MinusCsvParser.kt`, worker-driven via
`presentation/ui/settings/csv/CsvTransferManager.kt`). That means users can
archive, but not **migrate**: someone coming from another budget app (or a
bank statement export) has no way in. This plan adds a foreign-CSV import with
a column-mapping step and strict validation, reusing the existing
import-transaction pipeline.

## Conventions (every round-5 plan)

- User-facing strings in `app/src/main/res/values/strings.xml` **plus
  `values-es` and `values-fr`** (other locales are Crowdin-managed).
- Money = `BigDecimal`/plain strings, never `Double` — CSV amount parsing must
  produce `BigDecimal` via `toBigDecimalOrNull()`, reject otherwise.
- Pure parsing/mapping logic in `domain/` (or `data/csv` following the existing
  pattern) with JUnit4 + Truth tests.
- Conventional commits, one commit for this plan.
- Global verification gate:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

## Current state (verified)

- `app/src/main/java/com/sachit/moneypal/data/csv/MinusCsvParser.kt` line 23:
  `fun parse(inputStream: InputStream): CsvImportPayload` — parses the
  MoneyPal format with a fixed header contract.
- `CsvModels.kt`: `CsvTransactionRow` → `toDomainTransaction()`;
  `CsvImportPayload`; `CsvImportResult(imported, discarded, errors)`.
- `CsvImportWorker` (same package) reads a SAF uri extra
  (`KEY_INPUT_URI`), opens a stream, calls
  `csvService.importTransactions(stream)` — see it for the exact error
  reporting path via `ErrorLogRecorder`.
- `MinusCsvServiceTest.kt` + `MinusCsvRoundTripTest.kt` show the test style
  (`csvOf(...)` helpers, in-memory fakes).
- Import UX entry: `CsvTransferManager.kt` enqueues the worker with a uri the
  user picks via SAF.

## Steps

### Step 1 — Sniffing + foreign parsing (pure Kotlin)

New `app/src/main/java/com/sachit/moneypal/data/csv/ForeignCsvParser.kt`:

- Sniff delimiter (`;` vs `,`) from the first line outside quotes.
- Parse into `List<Map<String, String>>` keyed by raw header cell (trim BOM,
  lowercase keys, strip quotes). No date/amount interpretation here.
- If the header set exactly matches the MoneyPal format's known columns,
  return `MoneyPalFormat` and let the **existing** parser handle the file —
  foreign path never touches it.

### Step 2 — Column mapping + row validation (pure Kotlin, fully unit-tested)

New `app/src/main/java/com/sachit/moneypal/domain/csv/CsvColumnMapper.kt`:

- `data class ColumnMapping(amount: Int?, date: Int?, comment: Int?, category: Int?, isIncome: Int?, paymentMethod: Int?)`
  with sensible auto-guessing: header names containing
  `amount|betrag|importe|monto` → amount, `date|datum|fecha` → date,
  `note|comment|description|beschreibung|concepto` → comment, etc.
- `data class MappedRow` with parse results; **row-level validation**:
  - amount: strip currency symbols/spaces, normalize decimal comma vs dot
    (detect from the first unambiguous row), `toBigDecimalOrNull()` else
    reject row with a reason;
  - date: try `ISO_LOCAL_DATE`, then `dd/MM/yyyy`, `MM/dd/yyyy`,
    `dd.MM.yyyy`, `yyyy-MM-dd HH:mm` — ambiguous formats (both day/month ≤ 12)
    resolve by the first successful parse of the whole file with one format
    (consistency rule), documented in KDoc;
  - sign convention option: "negatives are expenses" vs "abs value"
    (user-selectable in UI, passed in as a parameter).
- Output: `MappingOutcome(rows: List<MappedRow>, rejected: List<RejectedRow>)`
  where `RejectedRow(lineNumber, reason)`.

### Step 3 — Ingestion through the existing pipeline

Map accepted rows into `CsvTransactionRow` (id = 0, `clientGeneratedId` set to
a deterministic hash of `date|amount|comment` for dedupe — the DB already
guards on `clientGeneratedId`, see `TransactionDao.existsByClientGeneratedId`)
and feed them through `MinusCsvService.importTransactions`'s row-insertion
path (extract a package-private method if the public one is format-bound;
do not duplicate insertion logic).

### Step 4 — UI: mapping screen

New `presentation/ui/settings/csv/ForeignCsvImportScreen.kt`:

- User picks a file (same SAF flow as existing import), app shows a preview
  of the first ~20 rows with a dropdown per field (amount, date, comment,
  category-by-name, income-flag, payment-method) pre-filled from auto-guess,
  plus sign-convention and category-missing fallback ("Other" / create-new).
- Confirm → enqueue import (reuse `CsvImportWorker` or add
  `ForeignCsvImportWorker` if cleaner — your call, but reuse the
  `ErrorLogRecorder` pattern either way). Result summary reuses
  `CsvImportResult` (imported/discarded/errors list).
- Entry point: Settings → Data section, under the existing CSV row; new
  strings `csv_foreign_import_*` (all three locales).

## Out of scope

- Editing the MoneyPal-format parser or its format (round-trip must stay
  byte-stable).
- Bank-specific statement formats beyond generic column mapping; no PDF
  statement parsing.
- Auto-categorization beyond exact name matching to existing categories
  (SMS-derived suggestion engine is a separate concern).
- Wear module.

## Test plan

- `ForeignCsvParserTest.kt`: delimiter sniffing (comma/semicolon), quoted
  fields with embedded commas/newlines, BOM stripping, MoneyPal-header
  detection.
- `CsvColumnMapperTest.kt`: amount comma/dot normalization; each supported
  date format; ambiguity consistency rule; negative-sign conventions;
  rejection reasons carry line numbers; dedupe hash stability.
- `MinusCsvRoundTripTest` still green (no change to native format).

## Done criteria (machine-checkable)

1. `./gradlew :app:testFossDebugUnitTest` — exit 0 including the two new test
   classes.
2. Compile gate — exit 0.
3. `grep -rn "ForeignCsvParser\|CsvColumnMapper" app/src/main --include="*.kt"`
   — matches in `data/csv/` + `domain/csv/` + UI wiring.
4. Manual: import a semicolon-delimited European bank CSV (amounts with
   comma decimals) → mapping screen guesses correctly → import completes with
   0 unexpected discards; re-import the same file → all rows deduped
   (imported = 0).

## Maintenance notes

- The mapping model is deliberately row-based and stateless; if a future plan
  adds OFX/QIF import, it should produce `MappedRow`s too so validation stays
  in one place.
- Do not let the foreign path write `source` = "sms" or any capture
  confidence — imported rows are manual-equivalent (leave `source` null).

## Escape hatches

- If `MinusCsvService.importTransactions` turns out to be too entangled with
  the native format to extract a shared insertion path, STOP and report the
  entanglement — do not fork insertion logic into the new parser.
- If dedupe via `clientGeneratedId` collides with the worker's REPLACE policy
  (rows silently overwritten instead of skipped), report — the semantics must
  be skip-or-replace by explicit choice, not accidental.
