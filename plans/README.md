# MoneyPal — Implementation Plans (round 4)

> Written against commit `675fce7` (2026-09-17) by an `/improve` audit.
> Round 3 (plans 012–022) is fully executed and its docs were removed; this
> round focuses on correctness bugs found in the backup/restore, SMS-capture,
> and SQL layers, plus a test safety net and worker error-contract hardening.
> Watch-related (`:wear` module) features remain excluded per maintainer request.

Build prerequisites (every plan): JDK 17/21, Android SDK 36, flavor-qualified
Gradle tasks. Global verification gate (run at the end of every plan):

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin :sync-contract:compileKotlin
./gradlew :app:testFossDebugUnitTest :sync-contract:test
```

Conventions for executors: user-facing strings in `values/strings.xml` (+ es/fr
copies), money = `BigDecimal`/plain strings never floats/`Double`, MVI
contracts per screen, pure logic in `domain/` with JUnit4 + Truth tests,
conventional commits, one commit per plan.

## Status

| # | Plan | Type | Priority | Depends on | Status |
|:--|:-----|:-----|:---------|:-----------|:-------|
| [023](023-sms-undo-real-transaction-id.md) | SMS undo: return the real inserted row id | fix (user-visible) | 0 | — | TODO |
| [024](024-duplicate-check-affinity-hardening.md) | Duplicate check: exact + REAL belt-and-braces | fix (hardening) | 4 | — | TODO |
| [025](025-restore-id-integrity.md) | Restore: remap paid-occurrence + category ids | fix (data integrity) | 2 | 028 | TODO |
| [026](026-remove-backupdates-dead-code.md) | Delete dead BackupDates/backupString helpers | chore | 5 | — | TODO |
| [027](027-decimal-totals-in-sql.md) | Spend totals: sum BigDecimal, not SUM(CAST AS REAL) | fix (precision) | 1 | — | TODO |
| [028](028-restore-characterization-tests.md) | Characterization tests for backup/restore | tests | 0 — lands first | — | TODO |
| [029](029-background-job-error-contract.md) | Workers: bounded retries + correct completion results | fix (reliability) | 3 | — | TODO |

## Recommended execution order

1. **028** (characterization tests) first — it pins current restore behavior so
   **025** cannot silently change what it must not change.
2. **023** (SMS undo) — smallest user-visible fix; independent.
3. **027** (decimal totals) — independent; touches DAO + repository only.
4. **025** after 028 lands; independent of everything else.
5. **029**, **024**, **026** round out the round; all independent.

Dependency rules: only 025 depends on 028. No plan adds a Room migration, so
the one-migration-per-train rule is not exercised this round. No plan touches
the watch module.

## Considered and rejected (do not re-audit)

- **Watch-side features / watch sync enhancements** — excluded by maintainer
  request (carried from round 3).
- **Per-bank SMS parser templates** — contradicts the documented generic-parser
  design decision (`BankSmsParser` header, decided 2026-09-08).
- **Migrating to `androidx.security-crypto`** — rejected in round 2; the custom
  Keystore wrapper (`BackupPasswordStore`) is deliberate.
- **`BackupEncryption` scheme** — audited round 4: AES-256-GCM, per-file salt/IV,
  210k PBKDF2 iterations, constant-failure decrypt path. Correct as-is; do not
  flag or "modernize".
- **Honoring `https_proxy`-style env in networking code** — not applicable
  (carried from round 3).
- **`QuickAmountPicker` BigDecimal key handling** — audited round 4: the
  plain-string key merge for 50.00/50 is correct; no finding.
- **`ProcessIncomingSmsUseCase` dedupe/error ordering** — audited round 4:
  plan-012 semantics correctly implemented (mark-seen failure swallowed,
  `Result.Error` reserved for insert failures). No finding.
- **AutoBackupScheduler pruning/cadence math** — correct; the only issue is the
  worker's misuse of `Result.retry()` for a "not due" outcome (plan 029).
