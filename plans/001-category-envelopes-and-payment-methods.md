# Plan 001 — Category envelopes + payment-method tags (one shared Room 19→20)

## Summary
Two data-model features in a single migration:
- **Envelopes**: optional `monthlyLimit` on categories. Analytics shows progress bars; overspend gets a warning color/label.
- **Payment methods**: `paymentMethod` on transactions (`CASH`/`CARD`/`OTHER`), default `OTHER` for legacy rows. Selectable in the editor; shown on the expanded expense row; filterable in History.

## Database
- `CategoryEntity` + `Category`: add `val monthlyLimit: BigDecimal? = null` (stored as TEXT, same convention as amount columns — money never touches floating point).
- `TransactionEntity` + `TransactionModel`: add `val paymentMethod: String = "OTHER"` (enum name; keep as String in DB).
- Bump `AppDatabase` version 19→20; add `AutoMigration(from = 19, to = 20)`.
- Export `20.json`: build at v19 with new columns stripped (round-3 bootstrap pattern), then bump and build again to export the real 20.json.

## UI / logic
- Editor: payment-method selector (three chips) near the category row; passes through MVI intent.
- `ExpenseItemExpandedContent`: small icon/text for method (cash 💵 / card 💳 / other ·) when not `OTHER`.
- History: filter chip group for payment method, composed with existing category/amount filters (see `HistoryViewModel` filter state).
- Analytics: per-category envelope progress — spend vs `monthlyLimit` for the current period; progress bar color turns warning on ≥90%, error on overspend. Only categories with a limit set are shown.
- Category style sheet (`CategoryStyleSheet`): add an optional limit input field.

## Backup
- `BackupCategory` gains `monthlyLimit: String? = null`; `BackupTransaction` gains `paymentMethod: String = "OTHER"`; both carried through `CreateBackupUseCase`/`RestoreBackupUseCase` with `ignoreUnknownKeys` semantics already in place.

## Out of scope
- Rollover of unused envelope amounts (noted for future).
- Multi-currency conversion of limits.

## Verification
- `./gradlew :app:testFossDebugUnitTest` — all pass, incl. new unit tests for envelope progress calculation (pure calculator in `domain/calculator/`, following `NoSpendStreakCalculatorTest` pattern).
- `./gradlew :app:verifyPaparazziFossDebug` — refresh goldens for expanded-row + analytics additions.
- Migration gate: install-at-19 → upgrade path covered by AutoMigration + schema export presence.
