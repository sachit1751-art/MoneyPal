# MoneyPal — Implementation Plans (round 6)

> Written 2026-09-23 against `48bb35b` (round-4 fixes landed; plan 041 of the
> retired round-5 set implemented, uncommitted at time of writing). Round 5
> (030–041) was retired by maintainer decision on 2026-09-23; the ideas that
> still had merit were re-audited and either re-planned (reduced) or parked in
> [050](050-backlog-parked-ideas.md).

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
`domain/` with JUnit4 + Truth tests, conventional commits, one commit per
plan. Room schemas are exported (`app/schemas/`, database currently at
**version 23**) — any schema-touching plan must bump the version, register an
auto-migration, and commit both schema JSONs.

## Status — round 6

| # | Plan | Type | Priority | Depends on | Status |
|:--|:-----|:-----|:---------|:-----------|:-------|
| [042](042-widget-privacy-redaction.md) | Widget privacy redaction (hide amounts) | feature (S) | 1 | 041 | DONE (`e1be988`) |
| [043](043-cash-wallet-balance.md) | Cash + wallet balance tracker (Room 23→24) | feature (M) | 2 | — | DONE (`7eca5b9`) |
| [044](044-monthly-report-pdf.md) | Monthly spending report (shareable PDF) | feature (M) | 2 | 027 ✓ | DONE (`fe42de1`) |
| [045](045-notification-actions.md) | Notification actions: mark paid / snooze | feature (S–M) | 2 | 029 ✓ | DONE (`68949cc`) |
| [046](046-income-analytics.md) | Income vs spend analytics card | feature (M) | 3 | 027 ✓ | DONE (`e68c1ce`) |
| [047](047-subscription-price-history.md) | Subscription price-change detection | feature (S) | 3 | — | DONE (`9781613`) |
| [048](048-sms-capture-opt-in-keyword.md) | SMS capture per-sender mute list | feature (S–M) | 3 | 014 ✓ | DONE (`c7bc024`) |
| [049](049-data-dashboard.md) | Data health dashboard | feature (S–M) | 4 | 024/025/028 ✓ | DONE (`aab9688`) |
| [050](050-backlog-parked-ideas.md) | Parked ideas backlog | ideas | — | — | — |

✓ = dependency already landed (round 4).

## Recommended execution order

1. **042** (widget redaction) — smallest, completes the 041 privacy story;
   commit the uncommitted 041 work first.
2. **045** (notification actions) and **044** (PDF report) — independent,
   high user visibility.
3. **043** (cash balance) — lands Room 23→24; execute before any other
   schema-touching idea to avoid parallel migration conflicts.
4. **046 → 047 → 048 → 049** — analytics and quality-of-life tail.

## Round-6 themes

- **Finish stories instead of starting them**: 042 completes 041; 044/045
  complete the reporting and reminder loops; 043 completes `PaymentMethod`.
- **Reuse landed round-4 machinery**: BigDecimal SQL (027), occurrence
  remapping (025), worker contracts (029), characterization tests (028).
- **Respect standing decisions**: single generic SMS parser (no per-bank
  templates), `:wear` excluded, no multi-account schema without a proven need.
