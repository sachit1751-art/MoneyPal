# Plan 048 — SMS capture: per-sender mute list

**Status:** DONE — `c7bc024`
**Written against commit:** `48bb35b` (2026-09-23)
**Category:** feature (S–M)
**Depends on:** plan 014 (SMS capture metadata, landed)
**Effort:** S–M · **Risk:** low

## Why

SMS capture runs a generic parser (deliberate design, 2026-09-08) over every
incoming bank SMS. Some senders — OTP codes, promotional blasts, low-value
merchants — produce review-inbox noise the user can never silence. A
per-sender mute list (and a one-tap "mute this sender" from the low-confidence
review inbox) gives users control without violating the generic-parser design.

## Current state (verified)

- Pipeline: `ProcessIncomingSmsUseCase` → `SmsIngestWorker` →
  `BankSmsParser` (single generic parser; per-bank templates are explicitly
  forbidden by the AGENTS.md convention).
- Low-confidence review inbox exists in History (plan 015).
- `Transaction.source = "sms"` with `captureConfidence` 0..100.
- DataStore string-set pattern already exists
  (`stringSetPreferencesKey` in `SettingsRepositoryImpl.kt` imports).

## Steps

1. **DataStore**: `muted_sms_senders` as a `Set<String>` (sender addresses
   stored exactly as received, normalized case-insensitively at compare
   time); repo getter/setter + `UserSettings` field; round-trip +
   empty-default tests.
2. **Gate**: filter in `ProcessIncomingSmsUseCase` (before parsing) — muted
   senders short-circuit with a documented result variant (not an error; per
   plan 029's error contract, workers must report completion correctly).
   Never touch `BankSmsParser` itself (convention).
3. **UI**: "Muted senders" management list in the SMS capture settings block
   (count subtitle, un-mute per row); "Mute this sender" action on
   review-inbox rows and on the SMS-undo notification (plan 023) as a third
   action where space allows. Strings ×3 locales.
4. **Telemetry-free**: no counters, no upload — pure local preference.

## Out of scope

- Per-keyword mute (senders only), auto-mute heuristics, parsing changes,
  wear.

## Test plan

- Use-case test: muted sender → no parse, no insert, documented result;
  case-insensitive match; unmuted → unchanged behavior.
- Repo round-trip for the set.

## Done criteria

1. `grep -rn "muted_sms_senders" app/src/main --include="*.kt"` — repo +
   use-case references.
2. Verification gate exit 0.

## Escape hatches

- If sender addresses vary in formatting for the same bank (short codes vs
  alphanumeric IDs), report real examples before adding normalization rules —
  do not silently strip characters in the gate.
