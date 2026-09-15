# MoneyPal — feature round 4 (fresh round, written 2026-09-15)

Written against commit **`5023888`** (`git rev-parse --short HEAD` must print
`5023888`). Previous round-1/2/3 plan sets were deleted at the maintainer's
request once their status was confirmed all-DONE (round-3b backlog items that
never shipped — envelopes, savings goals, digest, shortcuts, etc. — are
re-planned here).

## Scope

Six user-selected features, executed this session:

| Order | Plan | Summary | Migration | Status |
|:------|:-----|:--------|:----------|:-------|
| 1 | `001-category-envelopes-and-payment-methods.md` | Per-category monthly limits + cash/card/other payment-method tags; **single shared Room 19→20 migration** | Room 19→20 | DONE |
| 2 | `002-tracked-savings-goals.md` | Progress toward the existing savings goal (saved-per-period, progress bar, ETA) | none | DONE |
| 3 | `003-weekly-digest-and-shortcut.md` | Opt-in Monday-morning digest notification + static "Add expense" home-screen shortcut | none | DONE |
| 4 | `004-auto-backup-saf.md` | Scheduled full-data backup into a user-picked SAF folder (15-day cadence, opt-in) | none | DONE |
| 5 | `005-notification-quick-add.md` | Reply "12.5 groceries" to period/subscription notifications to log an expense | none | DONE |

## Round-4 execution notes (2026-09-15)

All five plans were executed and verified: `:app:testFossDebugUnitTest` passes
(650+ tests incl. the new `SavingsGoalCalculatorTest`), both debug APKs build,
and `schema 20.json` was committed. Deviations from plan text: the weekly
digest uses `EntryPointAccessors` (matching `RecurrentExpenseNotificationWorker`)
rather than `@HiltWorker`; the savings-goal saved amount is derived from
archived budgets' savings share rather than a new transaction field; the
quick-add action ships on the period-end notification channel first; the
`es-ES`/`fr` string translations for the new features are pending (Crowdin).

## Key ground rules for executors

- Verification gates are flavor-qualified — `:app` has no default flavor.
  Full gate: `:app:testFossDebugUnitTest` and `:app:verifyPaparazziFossDebug`
  (JDK 17–21 required; wrapper fails on JDK 25).
- One **shared** Room migration 19→20 carries both `category.monthlyLimit` and
  `transactions.paymentMethod` — do not create two migrations for this round.
- New schema export `app/schemas/.../20.json` **must** be committed.
- Strings in `values/strings.xml` (+ es/fr translations where the pattern file
  exists), MVI intent/controller patterns, `TimeProvider` for anything
  date-dependent (established round-1 pattern).
- The Gradle changelog generator skips versions whose tag doesn't exist yet —
  expected pre-release; the new changelog entries land in the next
  `prep_release`.
