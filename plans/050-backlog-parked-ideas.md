# Plan 050 — Round-6 idea backlog (not planned yet)

**Status:** IDEAS — deliberately unplanned
**Written against commit:** `48bb35b` (2026-09-23)

Ideas surfaced by the round-6 audit but **not** promoted to full plans. Each
notes why it was parked. Promote one to a full plan (verified current-state
section, steps, tests, done criteria) only with maintainer sign-off.

| Idea | Type | Why parked |
|:-----|:-----|:-----------|
| CSV import with foreign formats + column mapping | feature (M) | Was round-5 plan 030 (never executed). Real value, but needs format samples from the maintainer to plan the mapping UI honestly. Park until sample files exist. |
| Multiple accounts/wallets + transfers | feature (L) | Was round-5 plan 036. Largest schema change in the backlog (every transaction gets an account FK); plan 043's single cash balance covers the 80% case cheaply. Revisit only if 043 proves insufficient. |
| Bulk edit (multi-select) in History | feature (S–M) | Was round-5 plan 037. Solid, but History's MVI contract is already large; worth doing after 046/049 touch the same screen. |
| Custom / payday-aligned budget periods | feature (M) | Was round-5 plan 038. Touches `BudgetCalculator.calculate` + period end math used everywhere; deserves its own audit round. |
| Year in review screen | feature (delight) | Was round-5 plan 032. Fun but zero corrective value; PDF report (044) covers the "summarize my year" need partially. |
| Wear: budget tile on watch face | feature (M) | Maintainer excluded `:wear` features by request (round-4/5 plans). Only if that stance changes. |
| Attachment browser / receipt gallery | feature (M) | Attachments exist (`attachmentUri`, `AttachmentStore`) and plan 049 surfaces missing files, but no one has asked to browse receipts. Park until requested. |
| Undo window for any transaction delete | feature (S) | Plan 023 built undo for SMS-captured entries; generalizing is easy but unverified whether plain deletes need it (users may rely on the confirm dialog). Needs a quick user signal first. |
| Widget per-period selection (current/previous) | feature (S) | Widgets currently show the active period only; previous-period widgets risk stale-data confusion at period boundaries. Defer until a user asks. |
| Encrypted backup format v2 (per-field encryption) | chore (L) | Encrypted backups exist; a format bump breaks restore compatibility for zero user-visible gain. Only with a concrete threat model. |

## Promotion criteria

A parked idea becomes a plan when: (1) the maintainer approves, (2) a
verified current-state section can be written (facts, not guesses — see the
round-4 chat's premature-deletion lesson), and (3) it does not collide with
in-flight plans in this round.
