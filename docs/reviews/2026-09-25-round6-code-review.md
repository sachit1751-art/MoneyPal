# Round-6 code review — plans 041–049

**Reviewed:** 2026-09-25
**Range:** `48bb35b...HEAD` (13 commits: plan 041 + round-6 plan docs + plans 042–049 + housekeeping)
**Scope:** 103 files changed, +5524/−3141
**Method:** two-axis review — Standards (repo documented standards + Fowler smell baseline) and Spec (the `plans/042–049-*.md` docs as the originating specs). No issue tracker is configured (`docs/agents/issue-tracker.md` missing); the plan docs served as the spec source.

Commits in range:

```
da85d06 docs(plans): mark plans 042-049 done in their own docs
43b187b chore: ignore local freebuff chat logs
67d9aff docs(plans): mark round-6 plans 042-049 as done in the index
aab9688 feat: read-only data health dashboard (plan 049)
c7bc024 feat: per-sender mute list for SMS capture (plan 048)
9781613 feat: detect and badge subscription price changes (plan 047)
e68c1ce feat: income vs spend comparison card in Analytics (plan 046)
7eca5b9 feat: cash on hand balance tracker (plan 043)
fe42de1 feat: shareable one-page period report as PDF (plan 044)
68949cc feat: mark-paid and snooze actions on recurring payment reminders (plan 045)
e1be988 feat: hide amounts on home-screen widgets (plan 042)
ed15c8c docs(plans): round-6 feature plans 042-050; remove executed round-4/5 docs
c3213d7 feat: auto-lock timeout options and FLAG_SECURE privacy hardening (plan 041)
```

---

## Standards

**Hard violations of documented standards: none.**

- **Old name/package ban** (AGENTS.md): zero hits for `serranoie`/`ISAAC` in `app/src/main`.
- **Money = BigDecimal, never Double/CAST AS REAL** (AGENTS.md): no `Double`/`toDouble` in any new domain code; no `CAST` in any DAO.
- **User-facing strings in resources** (CONTRIBUTING.md): every added `text =`/`label =` uses `stringResource`; the one concatenation (`"$totalSpentLabel: "` + masked amount) builds from an already-localized label. No hardcoded Toast text. Strings were added to `values/`, `values-es/`, `values-fr/` in every feature commit (63 added lines each, matching).
- **Commits**: all 13 follow conventional commits with the required Codebuff footer.
- **Tests**: every feature shipped a JVM test (calculator/scanner/handler/use-case/repo), per the "pure logic in `domain/` + JUnit4 + Truth" rule. `DataHealthScanner` has no Android imports — the domain-purity rule holds.

### Baseline smells (Fowler, ch. 3) — all judgement calls

1. **Possible Data Clump / Primitive Obsession** (plan 045): `transactionId: Long` + `occurrenceDateEpochDay: Long` + `notificationId: Int` travel as loose extras through worker → helper → receiver. A small `OccurrenceRef` value type would bind them. Not worth churn on its own.
2. **Possible Shotgun Surgery / Duplicated Code** (plans 042, 045, 048): adding one settings toggle touches ~7 files (key const → impl → interface → `UserSettings` → VM → `Settings.kt` → `SettingsScreen.kt` → 3 string files), and redaction params are threaded individually through all 7 widgets. **Suppressed by repo-override**: this is the repo's established DataStore/EntryPoint pattern, followed deliberately.
3. **Minor naming observation**: plan 047's badge row lives under `presentation/ui/theme/component/` — a component in the `theme` package. Pre-existing repo structure, not introduced by this diff.

*Footnote (out-of-diff): `CONTRIBUTING.md` says "JDK 17" while AGENTS.md pins JDK 21 — pre-existing doc drift worth fixing someday.*

---

## Spec

All 8 plans' core deliverables and done-criteria greps verify (actions / `PdfDocument` / `DataHealthScanner` / masked widgets all present; 043 committed `24.json` and bumped the DB to version 24). Findings below are all "partial," none incorrect.

1. **043 — missing migration test** (spec: *"Room migration test for 23→24 (androidTest pattern: AppDatabaseMigrationTest)"*). The migration landed but `AppDatabaseMigrationTest` still covers only 13→16. Most concrete miss in the round.
2. **044 — partial entry point** (spec: *"entry point in the export/settings area **and** a share icon on the Analytics period header"*). Only the Settings entry shipped.
3. **046 — tutorial strings missing** (spec: *"add matching strings for the new card"* referencing `analytics_tutorial_*`). The card has 5 display strings but no tutorial entries.
4. **047 — simplified affordances** (spec: *"one-tap 'Update template amount' action … dismiss = keep old template"*). The badge shows and tap-to-edit pre-exists on the row, but there's no dedicated update action and no dismiss marker.
5. **048 — mute entry points missing** (spec: *"'Mute this sender' action on review-inbox rows and on the SMS-undo notification"*). Only the Settings management list shipped; the inbox/notification actions don't exist.
6. **049 — no Review routing** (spec: *"issues … get a 'Review' action routing to History pre-filtered"*). `IssueRow` is display-only. Defensible under the plan's "read-only v1" note, but the affordance isn't there.
7. **042 — toggle placement deviation** (spec: *"toggle inside the widgets sheet (WidgetGallerySheet.kt area)"*). Implemented in a Settings group instead. Cosmetic.

**Scope creep: none found.** The plan-045 snooze went beyond spec (dedicated unique-work name so snoozing doesn't jump the schedule) — a justified, documented improvement, not unrequested behavior.

**Nothing implemented looks wrong:** spot-checked semantics (adjustment-replace documented in KDoc, quick-actions default ON, widgets-hide default OFF, `compareTo` money equality in 047) all match spec intent.

---

## Summary

- **Standards:** 0 hard violations, 4 judgement-call smells (2 suppressed by documented repo patterns); worst = the untyped occurrence-extras clump (plan 045).
- **Spec:** 7 findings, all "partial," none incorrect; worst = the missing 23→24 migration test (plan 043).

## Suggested follow-up work

1. Add the Room 23→24 migration test to `AppDatabaseMigrationTest` (plan 043 gap).
2. Implement the remaining step-3 UI affordances: mute-from-review-inbox and mute-on-SMS-undo-notification (048), Review routing from data-health issue rows (049).
3. Add the Analytics period-header share icon (044) and tutorial strings for the income vs spend card (046).
