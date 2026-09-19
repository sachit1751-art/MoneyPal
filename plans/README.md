# MoneyPal — Implementation Plans

Two plan tracks live here:

- **Round 4 (audit fixes)** — plans 023–029, written 2026-09-17 against
  `675fce7`. Correctness/hardening for backup/restore, SMS capture, SQL
  precision, and worker contracts. All TODO at time of writing.
- **Round 5 (features)** — plans 030–041, written 2026-09-19 against
  `467ce9d` by a `/improve next`-style direction audit. Twelve maintainer-
  approved feature plans. **Round 5 plans assume round 4 has landed** where
  noted (backup/restore-touching features depend on plan 028's
  characterization tests landing first).

Build prerequisites (every plan): JDK 21 for the Gradle daemon (pinned via
`gradle/gradle-daemon-jvm.properties`; Gradle 9.5.0 + AGP 9.3.3), Android SDK
36, flavor-qualified Gradle tasks. Global verification gate (run at the end of
every plan):

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

Conventions for executors: user-facing strings in `values/strings.xml` (+ es
and fr copies; other locales are Crowdin-managed), money = `BigDecimal`/plain
strings never floats/`Double`, MVI contracts per screen, pure logic in
`domain/` with JUnit4 + Truth tests, conventional commits, one commit per plan.

## Status — round 5 (features)

| # | Plan | Type | Priority | Depends on | Status |
|:--|:-----|:-----|:---------|:-----------|:-------|
| [030](030-csv-import-foreign-formats.md) | CSV import: foreign formats + column mapping | feature (medium) | 2 | 029 preferred | TODO |
| [031](031-spending-calendar-heatmap.md) | Spending calendar heatmap in History | feature (medium) | 3 | — | TODO |
| [032](032-year-in-review.md) | Year in review summary screen | feature (delight) | 4 | 027 preferred | TODO |
| [033](033-recurring-payment-detection.md) | Recurring-pattern detection + one-tap template | feature (medium) | 2 | — | TODO |
| [034](034-upcoming-payments-timeline.md) | Upcoming payments timeline + committed total | feature (S–M) | 1 | — | TODO |
| [035](035-smart-insight-cards.md) | Smart insight cards (rule-based anomaly detection) | feature (medium) | 2 | 027 preferred | TODO |
| [036](036-multiple-accounts-wallets.md) | Multiple accounts/wallets + transfers | feature (L) | 5 | **028, 025** | TODO |
| [037](037-bulk-edit-history.md) | Bulk edit (multi-select) in History | feature (S–M) | 1 | — | TODO |
| [038](038-custom-budget-periods.md) | Custom & payday-aligned budget periods | feature (medium) | 3 | 028 preferred | TODO |
| [039](039-monthly-report-pdf-export.md) | Monthly report PDF export/share | feature (medium) | 3 | 027 preferred | TODO |
| [040](040-notification-actions-mark-paid-snooze.md) | Notification actions: mark paid / snooze | feature (S–M) | 2 | 029 preferred | TODO |
| [041](041-privacy-auto-lock-timeout-flag-secure.md) | Auto-lock timeout + screenshot blocking | feature (S) | 1 | — | TODO |

## Recommended execution order

Within round 5 (after the round-4 fixes, or interleaved where independent):

1. **Quick wins first:** 041 (auto-lock/FLAG_SECURE), 037 (bulk edit),
   034 (timeline) — small, independent, immediately visible.
2. **Pure-domain features (parallelizable):** 033 (recurring detection),
   035 (insights), 031 (heatmap).
3. **Data-in/data-out:** 030 (foreign CSV) after 029 (it touches
   `CsvImportWorker`), then 032 (year review), 039 (PDF report — reuses 032's
   aggregation shape).
4. **Structural, last:** 038 (periods) after 028; then 036 (accounts) — the
   only L-effort plan, gated on 028 + 025 landing first.
5. **040 (notification actions)** anytime after 029.

Dependency rules: 036 → {028, 025} (hard). Soft preferences (merge friction /
exactness only): 030 → 029; 032, 035, 039 → 027. Everything else independent.
Only plan 036 adds a Room migration (23→24) this round; 038 explicitly avoids
one. No plan touches the watch module (`:wear`); `:sync-contract` is unchanged.

## Considered and rejected (do not re-audit)

Carried from round 4 (see git history for the full prior list): watch-side
features (maintainer exclusion), per-bank SMS templates, `androidx.security-
crypto` migration, `BackupEncryption` scheme changes, `https_proxy` handling.

New this round:

- **Scheduled encrypted backup to SAF/WebDAV** — already implemented (plan
  004: `AutoBackupScheduler`/`AutoBackupWorker`, SAF tree URI, 15-day
  cadence; settings rows exist in `Settings.kt`). Replaced in the slate by
  plan 037 (bulk edit).
- **Tags on transactions** — the codebase's "tags" are numpad comment chips
  (`Editor.kt` `onDeleteTag`), not a tagging system; a real tag system was
  judged lower-leverage than the twelve selected features and cut during
  slate selection.
- **Quick-tile / shortcuts additions** — shortcuts (plan 003) and a quick-
  settings tile already exist.
