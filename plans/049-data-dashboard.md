# Plan 049 — Data health dashboard

**Status:** DONE — `aab9688`
**Written against commit:** `48bb35b` (2026-09-23)
**Category:** feature (quality-of-life, S–M)
**Depends on:** plans 024/025/028 (landed — they built the integrity machinery)
**Effort:** S–M · **Risk:** low

## Why

Rounds 4–5 added dedupe, restore remapping, characterization tests, and
attachment storage, but the user has no visibility into their own data's
state: how many transactions, how many SMS-captured, low-confidence count,
orphans, attachments whose files are missing. A read-only dashboard surfaces
issues and offers one-tap cleanup — turning internal integrity work into user
value.

## Current state (verified)

- `TransactionDao` counts rows; projections return raw amounts.
- `AttachmentStore.kt` manages receipt files (content URIs under app
  storage) — its load path can validate existence.
- SMS metadata: `source`/`captureConfidence` columns (plan 014, DB v23).
- Restore safety net: plan-028 characterization tests exist; any read-only
  scan cannot corrupt data.

## Steps

1. **Domain**: `DataHealthScanner` — pure aggregator over repository
   queries: total transactions, income vs expense counts, SMS-captured count
   + median confidence, rows with `attachmentUri` whose file is missing
   (check via `AttachmentStore`), duplicate `(clientGeneratedId)` count,
   oldest transaction date. Returns a `DataHealthReport` data class.
   Truth tests with synthetic rows.
2. **DAO additions**: count-only queries (`SELECT COUNT(*) ...`) per metric —
   counts, never full-row loads (data sets can be large).
3. **UI**: Settings → "Data health" screen (or bottom-sheet): stat rows with
   icons; issues (missing attachments, duplicate ids) get an amber icon and
   a "Review" action routing to History pre-filtered (History already has
   filter state per `HistoryMviContract`). Read-only — no fix-all buttons in
   v1.
4. **Strings** ×3 locales.

## Out of scope

- Auto-repair (deleting orphans etc. — deliberate: report first, fix in a
  follow-up plan after real-world row inspection), CSV/backup of the report,
  wear.

## Test plan

- `DataHealthScannerTest`: empty DB, missing-attachment detection (temp file
  store fake), duplicate detection, median confidence with even/odd counts.
- ViewModel test: report → UI state mapping; issue rows only when >0.

## Done criteria

1. `grep -rn "DataHealthScanner" app/src/main --include="*.kt"` — domain +
   settings references.
2. Verification gate exit 0.

## Escape hatches

- If `COUNT` queries on `transactions` prove slow on large datasets (they
  won't at mobile scale, but if they do), cache the report in the ViewModel
  scope rather than adding indices without measured evidence.
