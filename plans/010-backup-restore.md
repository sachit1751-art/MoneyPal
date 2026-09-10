# Plan 010 — Backup & restore (local JSON snapshot)

Written against commit **`614cb49`** (`git rev-parse --short HEAD` should print
`614cb49`). If HEAD differs, re-verify excerpts before executing.

## Why this matters

CSV export exists (`MinusCsvExporter` writes transactions + archived budgets +
budget-settings metadata), but it does **not** include: user categories (with
usage counts), UserSettings, pending rollover, paid recurrent occurrences, or
queued transactions. And `backup_rules.xml` (plan 001, DONE) deliberately
**excludes the finance DB from cloud/device-transfer backups**, so a lost or
replaced phone loses everything not in the CSV. A one-tap local JSON backup +
restore fills the gap and is the classic antidote to the worst 1-star review
("switched phones, lost 2 years of data").

## Current state (verified excerpts)

- `BudgetRepository` (`data/repository/BudgetRepository.kt`) exposes everything
  a full snapshot needs: `getTransactions()`, `getBudgetSettings()`,
  `getActiveCategories()`/`getAllCategories()`, `getPaidRecurrentOccurrences()`,
  `getArchivedBudgets()`, plus writers `upsertTransactions`,
  `upsertArchivedBudgets`, `findOrCreateCategory`, `saveBudgetSettings`,
  `markRecurrentOccurrencePaid`.
- `SettingsRepository` exposes `observeSettings(): Flow<UserSettings>` and many
  setters; the simplest restore-safe subset is `setSavingsPreferences`,
  `setLanguage`, theme setters, `setCurrentPeriod(periodId, startedAt)`.
- CSV export/save pattern to Downloads:
  `presentation/ui/settings/csv/CsvTransferManager.kt` uses
  `MediaStore.Downloads` with `RELATIVE_PATH` (Q+) and falls back to
  `cacheDir/exports` + FileProvider share (`"${context.packageName}.fileprovider"`).
  Copy this pattern exactly for the backup file.
- kotlinx.serialization is already a dependency
  (`implementation(libs.kotlinx.serialization.json)` in `app/build.gradle.kts`)
  and used in `:sync-contract`.
- Room DB version is 17 (`AppDatabase.kt`); entities: Transaction, BudgetSettings,
  Category, QueuedTransaction, ArchivedBudget, PaidRecurrentOccurrence.
- Settings section UI with export/import rows lives in `Settings.kt`
  (the CSV entry point is `CsvTransferEntryPoint.kt` → `CsvTransferManager`).

## Design decisions

- **JSON via kotlinx.serialization, not another CSV dialect.** Money amounts
  serialize as strings (repo convention: money never floats — see plans/README
  "Amounts stored as String in Room — verified good"). LocalDate/LocalDateTime
  serialize as ISO-8601 strings via custom serializers.
- **Explicit backup model class**, versioned:

  ```kotlin
  @Serializable
  data class MoneyPalBackup(
      val schemaVersion: Int = 1,
      val exportedAtEpochMs: Long,
      val transactions: List<BackupTransaction>,
      val categories: List<BackupCategory>,
      val archivedBudgets: List<BackupArchivedBudget>,
      val paidOccurrences: List<BackupPaidOccurrence>,
      val budgetSettings: BackupBudgetSettings?,
      val settings: BackupSettings,   // only safe/persistable subset
  )
  ```

- **Restore is replace-upsert, not destructive.** Match transactions by
  `clientGeneratedId` when present (repo already has
  `existsTransactionByClientGeneratedId`), else by (date, amount, comment).
  Never delete rows absent from the backup.
- **File location**: `Download/MoneyPal-backup-<yyyy-MM-dd-HHmm>.json` via
  MediaStore (same code path as CSV), plus share intent. Restore uses
  `ACTION_OPEN_DOCUMENT` (SAF) — no storage permission needed.
- **UserSettings subset**: include theme/typography/contrast/colorScheme/
  dynamicColor/language/savingsPreferences/notification times ONLY. Exclude
  session-state keys (early finish, current period id, pending rollover,
  midnight transition, sms seen keys) — restoring those can corrupt period
  math.

## In scope

- New: `app/src/main/java/com/sachit/moneypal/data/backup/BackupModels.kt`
- New: `app/src/main/java/com/sachit/moneypal/data/backup/BackupCodec.kt` (serialize/deserialize + validation)
- New: `app/src/main/java/com/sachit/moneypal/domain/usecase/CreateBackupUseCase.kt`
- New: `app/src/main/java/com/sachit/moneypal/domain/usecase/RestoreBackupUseCase.kt`
- New: `app/src/main/java/com/sachit/moneypal/presentation/ui/settings/backup/BackupTransferManager.kt`
- `app/src/main/java/com/sachit/moneypal/presentation/ui/settings/Settings.kt` (+ `SettingsViewModel.kt`) — two new rows in the CSV/backup section
- `app/src/main/res/values/strings.xml`
- Tests: `app/src/test/java/com/sachit/moneypal/data/backup/BackupCodecTest.kt`, `.../RestoreBackupUseCaseTest.kt`

## Out of scope

- Scheduled automatic backups, cloud upload, encryption-at-rest (file is
  intentionally local-only; document in the settings summary string).
- Wear-side backup.
- Migrating CSV import — untouched.

## Steps

1. **BackupModels.kt.** Define the `@Serializable` data classes mirroring the
   six entities + settings subset. Amounts as `String` (BigDecimal
   `.toPlainString()`); dates as ISO strings. Include `schemaVersion = 1`.
   Add `@JvmStatic`-free top-level serializers
   `LocalDateSerializer`/`LocalDateTimeSerializer` (or use
   `kotlinx-serialization-datetime` if already resolved transitively — check
   `gradle/libs.versions.toml` first; if absent, hand-write the two serializers,
   ~15 lines each).

2. **BackupCodec.kt.** `encode(MoneyPalBackup): String` (pretty-print off,
   UTF-8) and `decode(String): MoneyPalBackup` that throws a domain exception
   `BackupFormatException(reason)` on unknown schemaVersion (>1) or missing
   required fields. Use `WearJson.json`-style configuration
   (`ignoreUnknownKeys = true`) — but define its own `Json` instance in the
   backup module, do not import from `:sync-contract`.

3. **CreateBackupUseCase.** Inject `BudgetRepository` +
   `SettingsRepository`. `.first()` each flow
   (`getTransactions()`, `getAllCategories()` — not active, so hidden
   categories survive — `getPaidRecurrentOccurrences()`,
   `getArchivedBudgets()`, `getBudgetSettings()`, `observeSettings()`),
   map to `MoneyPalBackup`. Transactions: exclude `isDeleted` soft-deleted
   rows? **No** — include them with their flag so a restore is lossless.

4. **RestoreBackupUseCase.** Decode → validate → for each collection:
   - categories: `findOrCreateCategory(name)` then preserve `usageCount`/
     `isHidden` via the entity updater (add a small
     `suspend fun upsertCategoryBackup(...)` to the repo interface + impl only
     if `updateCategory`-equivalent isn't already reachable; prefer reusing
     `CategoryDao.updateCategory` through the repository — extend
     `BudgetRepository` with `suspend fun upsertCategories(categories: List<Category>)`
     and implement with Room `@Upsert` or REPLACE inserts).
   - transactions: skip when `existsTransactionByClientGeneratedId` hits;
     else `upsertTransactions` (already exists).
   - paid occurrences: `markRecurrentOccurrencePaid(id, date)` per item.
   - archived budgets: `upsertArchivedBudgets` (exists).
   - budget settings: only if backup contains one AND the local DB has none
     (`getBudgetSettingsSync() == null`) — never overwrite a live budget.
   - settings subset: call the individual setters (never `setCurrentPeriod`).
   Return a `RestoreResult(counts...)` for the snackbar.

5. **BackupTransferManager.** Mirror `CsvTransferManager` structure:
   `exportBackup()` → MediaStore Downloads (Q+) / cache fallback + share intent
   (MIME `application/json`); `restoreFrom(uri: Uri)` → read via
   `contentResolver.openInputStream`, run `RestoreBackupUseCase` on
   `Dispatchers.IO`, Toast result (success with counts / failure message).
   Route errors through `ErrorLogRecorder.record` like the CSV manager does.

6. **Settings UI.** In `Settings.kt` backup/CSV section add rows "Create
   backup" and "Restore backup" (strings: `settings_backup_create`,
   `settings_backup_restore`, `settings_backup_summary` — "Full local backup
   including categories and settings. The file is not encrypted — store it
   safely."). Restore launches `ActivityResultContracts.OpenDocument` with
   `arrayOf("application/json")`; wire through a confirmation dialog before
   overwriting (reuse the repo's dialog style — see
   `DeleteRecurrentExpenseDialog.kt`).

7. **Tests.** `BackupCodecTest`: round-trip a representative backup
   (all fields populated, incl. BigDecimal amounts with trailing zeros, null
   subscriptionDay, deleted transaction) — assert deep equality after decode;
   corrupt JSON and unknown `schemaVersion: 99` produce `BackupFormatException`.
   `RestoreBackupUseCaseTest`: with faked repositories, a backup containing a
   duplicate transaction (same clientGeneratedId) is not double-inserted; a
   live local budget is not overwritten; settings subset applied; counts in
   `RestoreResult` match. Follow fake style from
   `SettingsRepositoryImplTest`/existing use case tests in
   `app/src/test/java/com/sachit/moneypal/domain/usecase/`.

## Verification gates

```bash
./gradlew :app:compileFossDebugKotlin
./gradlew :app:testFossDebugUnitTest
```

Manual: export on a device → clear app data → restore → verify budget, history,
categories and theme return; verify restoring a live-budget backup does not
clobber the current budget.

## Done criteria

- [ ] Round-trip export → restore preserves transactions (incl. deleted flags), categories with usage counts, archived budgets, paid occurrences, and the settings subset.
- [ ] Restore never deletes local data and never overwrites an existing budget.
- [ ] Verification gates green.

## Maintenance notes

- Bump `schemaVersion` whenever entities change materially; keep
  `ignoreUnknownKeys` so v1 backups always restore into newer schemas.
- If Room version bumps past 17 while this plan is in flight, no conflict —
  the backup is entity-level, not schema-level.

## Escape hatches — STOP and report if

- `BudgetRepository` lacks a usable category upsert path AND
  `CategoryDao.updateCategory` is unreachable from the repository layer —
  propose the interface extension before implementing it.
- `UserSettings` mapping in `SettingsRepositoryImpl` turns out not to expose
  per-field setters for the chosen subset.
- FileProvider authority `"${context.packageName}.fileprovider"` is absent from
  the manifest (the CSV fallback path depends on it; verify in
  `app/src/main/AndroidManifest.xml`).
