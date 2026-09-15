# Plan 004 — Auto-backup to a SAF folder

## Summary
Opt-in scheduled backup of the full data set (same JSON payload as the manual
backup) written into a user-chosen folder via the Storage Access Framework,
on a 15-day cadence.

## Implementation notes
- Reuse `CreateBackupUseCase` + the manual path's JSON serialization
  (`BackupTransferManager` / `RestoreBackupUseCase` roundtrip must stay
  compatible — same `BackupPayload`).
- Settings: new "Automatic backup" section in `SettingsScreen` — toggle +
  "Choose folder" row using `ACTION_OPEN_DOCUMENT_TREE` +
  `takePersistableUriPermission`; store the tree `Uri` string in DataStore
  (`autoBackupEnabled`, `autoBackupTreeUri`, `autoBackupLastRunAt`).
- `AutoBackupWorker` (`CoroutineWorker`, construction pattern matched to
  existing workers): checks enabled + tree URI present + ≥15 days since
  `autoBackupLastRunAt`; writes `moneypal-backup-<yyyy-MM-dd>.json` via
  `DocumentsContract.createDocument(..., "application/json", name)` into the
  tree; on success updates `autoBackupLastRunAt`. Old backup files (> 3
  newest kept) are pruned.
- Scheduling: enqueued as a unique periodic work (name
  `"auto_backup"`, 15-day interval, flex 1 day) when enabled; cancelled when
  disabled. Also a one-shot expedited attempt on app start when due (the
  periodic request alone can drift with Doze; keep it simple — periodic +
  last-run check covers it).
- Errors (folder revoked, IO): worker result `failure()` + a silent no-crash;
  settings row shows "last backup: <date>" and a manual "Back up now" button
  reusing the same write path for instant feedback.

## Strings
- ~5 new strings (+ es/fr).

## Out of scope
- Encryption (separate backlog item).
- Google Drive / third-party targets.

## Verification
- Unit-test the "should run now" decision function (pure: enabled/uri/lastRun
  inputs) — follow existing calculator-test style.
- `./gradlew :app:testFossDebugUnitTest` full gate.
