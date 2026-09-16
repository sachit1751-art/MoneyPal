# MoneyPal — Implementation Plans (round 3)

> Written against commit `ff6f773` (2026-09-16) by an `/improve` audit.
> Plans 001–005 and 006–011 from earlier rounds have landed; this round covers
> the audit findings plus 10 new features. **Watch-related (`:wear` module,
> Wear tile, watch sync) features are intentionally excluded** per maintainer
> request — the only cross-module work is a one-line APK rename (plan 020).

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
| [012](012-sms-dedupe-atomic.md) | SMS dedupe: atomic + bounded store | fix (audit #1/#2) | 0 — correctness first | — | DONE 2026-09-16 |
| [013](013-sms-parser-coverage.md) | SMS parser: real-world format coverage | feature (user priority) | 1 | — | DONE 2026-09-16 |
| [014](014-sms-smart-capture.md) | SMS capture: merchant + auto-category + confidence | feature (user priority) | 2 | 012, 013 | DONE 2026-09-16 (room 22→23) |
| [015](015-sms-review-inbox.md) | Low-confidence SMS review inbox | feature | 3 | 014 | DONE 2026-09-16 |
| [016](016-recurring-merchant-aliases.md) | Recurring payment merchant aliases | feature | 4 | — | DONE 2026-09-16 |
| [017](017-refund-tracker.md) | Refund tracker completion flow | feature | 5 | — | TODO |
| [018](018-burn-rate-forecast-card.md) | Analytics: burn-rate forecast card | feature | 6 | — | TODO |
| [019](019-monthly-report-export.md) | Monthly report share (text) | feature | 7 | — | TODO |
| [020](020-apk-rename-and-build-script.md) | APK rename + one-click build script wiring | DX (audit #7/#8) | 8 | — | TODO |
| [021](021-quick-expense-presets.md) | Quick-amount presets on the numpad | feature | 9 | — | TODO |
| [022](022-savings-goal-progress-widget.md) | Savings goal home-screen widget | feature | 10 | — | TODO (written 2026-09-16) |

## Recommended execution order

1. **012** (SMS correctness fixes) before anything SMS-related.
2. **013 → 014 → 015** form the "make SMS auto-parsing better" chain (user
   priority); 013 and 012 are independent and can be parallelized.
3. Independent batch any time: **016, 017, 018**.
4. **020** (DX) any time; land it before the next release train so the
   watch-flavor/watch-module APK name collision is gone before anyone runs
   `scripts/build-apks.sh`.
5. **019, 021, 022** round out the feature set.

Dependency rules: only ONE Room migration per release train — 014 and 016 both
add columns; if both land in the same train, chain them (014 documents the
version-shift rule). 015 needs 014's confidence model. No plan touches the
watch module except 020's one-line rename.

## Considered and rejected (do not re-audit)

- **Honoring `https_proxy`-style env in any networking code** — not applicable;
  the app's only network surface is Wear Data Layer + SAF file I/O.
- **Watch-side review inbox / watch tile enhancements** — excluded by
  maintainer request this round.
- **Migration to `androidx.security-crypto` for backup passwords** — rejected
  in round 2; the custom Keystore wrapper (plan 011) is kept deliberately.
- **Per-bank SMS parser templates** — contradicts the documented generic-parser
  design decision (see `BankSmsParser` header, decided 2026-09-08); plan 013
  extends the generic patterns instead.
