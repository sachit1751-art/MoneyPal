# 001 — Stop unencrypted cloud/device backup of the finance database

**Audit base:** commit `a2fda9c` — verify with `git rev-parse HEAD` before
starting; if it differs, stop and re-confirm the excerpts below.

## Why this matters

MoneyPal is a local-first budget app storing spending history, budget
settings, and future recurring charges in a Room database plus DataStore.
`AndroidManifest.xml` enables full Android auto-backup
(`android:allowBackup="true"` with `android:fullBackupContent` and
`android:dataExtractionRules`), and the two XML rule files it points at are
the untouched Android Studio **template** — every rule is commented out. With
empty rules, Android backs up *everything* (the SQLite DB, DataStore files,
shared prefs) to Google Drive and to device-to-device transfer. Cloud backups
are not end-to-end encrypted, so the user's full spending history sits
decryptable-by-provider in the cloud — undermining the app's local/privacy
positioning. This is a privacy decision, not an emergency: make it explicit
and intentional.

## Current state (verify before editing)

`app/src/main/AndroidManifest.xml` (application tag, ~lines 16–19):

```xml
<application
    android:name=".MinusApplication"
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    ...
```

`app/src/main/res/xml/backup_rules.xml` — entire content is comments:

```xml
<full-backup-content>
    <!--
   <include domain="sharedpref" path="."/>
   <exclude domain="sharedpref" path="device.xml"/>
-->
</full-backup-content>
```

`app/src/main/res/xml/data_extraction_rules.xml` — entire content is comments
(cloud-backup and device-transfer sections both empty).

## Decision (canonical)

Exclude **all application data** from cloud backup and device transfer by
declaring explicit `exclude` rules for the database, DataStore and
`filesDir`/shared-preferences domains, rather than flipping
`allowBackup="false"` (which would also block device-to-device transfer on
phone upgrades — many users value that for app data that is *not* financial).
Rationale: device transfer is local and encrypted in transit by the system;
cloud backup is the exposure we are closing. If a reviewer/maintainer prefers
the stronger `allowBackup="false"`, that is an acceptable alternative — but do
not silently pick it; the excluded-domains version below is the default.

## Steps

1. **Characterize what currently gets backed up.** Room DB lives under
   `databases/` (name is `transactions.db` or similar — confirm with
   `grep -rn "databaseBuilder\|\.db" app/src/main/java/com/sachit/moneypal/data/di/DatabaseModule.kt app/src/main/java/com/sachit/moneypal/data/local/AppDatabase.kt`).
   DataStore files live under `files/datastore/`. Confirm paths exist by
   checking `DataStoreModule.kt` for the `preferencesDataStore` file name
   (e.g. `"settings"` → `files/datastore/settings.preferences_pb`).

2. **Replace the content of `app/src/main/res/xml/backup_rules.xml`** (keep the
   file — it is referenced by the manifest) with explicit excludes:

   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <!--
       MoneyPal stores financial data locally on purpose. Keep it out of
       cloud backups; allow device-to-device transfer only for non-financial
       app data (see data_extraction_rules.xml for the API 31+ equivalent).
   -->
   <full-backup-content>
       <exclude domain="database" path="."/>
       <exclude domain="file" path="datastore/"/>
       <exclude domain="file" path="."/>
   </full-backup-content>
   ```

   Note: `<exclude domain="file" path="."/>` covers `filesDir` (incl.
   DataStore and the error log at `filesDir/error_log.txt`). The DataStore
   line is belt-and-braces documentation; keeping the broad file exclude is
   what guarantees the finance data stays out.

3. **Replace the content of `app/src/main/res/xml/data_extraction_rules.xml`**
   with the API 31+ equivalent:

   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <data-extraction-rules>
       <cloud-backup>
           <exclude domain="database" path="."/>
           <exclude domain="file" path="datastore/"/>
           <exclude domain="file" path="."/>
       </cloud-backup>
       <device-transfer>
           <!-- Financial DB and files stay on the device; nothing custom to
                transfer. Default rules otherwise apply to shared prefs. -->
           <exclude domain="database" path="."/>
           <exclude domain="file" path="datastore/"/>
       </device-transfer>
   </data-extraction-rules>
   ```

   Intent of the asymmetry: **cloud backup** excludes file-domain broadly
   (finance data, error log); **device transfer** still moves non-financial
   files/settings while excluding the DB + datastore. If you judge the error
   log safe for device transfer, that is fine — but the DB and DataStore
   excludes are mandatory in both sections.

4. **Verify resources compile:**
   `./gradlew :app:processFossDebugResources` — expect `BUILD SUCCESSFUL`.

## Done criteria (all must pass)

- [ ] `grep -c "<exclude" app/src/main/res/xml/backup_rules.xml` ≥ 3
- [ ] `grep -c "<exclude" app/src/main/res/xml/data_extraction_rules.xml` ≥ 5
- [ ] `grep -rn "allowBackup=\"true\"" app/src/main/AndroidManifest.xml` still
      returns the application tag (backup stays on — we exclude, not disable)
- [ ] `./gradlew :app:processFossDebugResources` → `BUILD SUCCESSFUL`
- [ ] No rule file contains leftover template comments that re-enable backup
      of `database` or `datastore`

## Test plan

No unit tests: this is XML resource configuration. The compile task above is
the gate. For manual QA on a device/emulator (optional, if available): enable
"Back up my data", reinstall the app after clearing data, and confirm
transactions do not reappear while e.g. a non-financial setting in
`sharedpref` does.

## Maintenance note

- Any future feature that persists sensitive data (e.g. encrypted backup
  keys, biometric salt) must add an `<exclude>` for its domain/path here or
  it will silently start leaking into cloud backups.
- `data_extraction_rules.xml` governs API 31+; `backup_rules.xml` governs
  API 27–30. Both must stay in sync — a future change touching one should
  touch the other.
- The Android Studio template comment at the top of `data_extraction_rules.xml`
  (a `TODO`) disappears once this plan lands; if a future IDE regenerates
  these files, re-verify excludes survived.

## Out of scope / boundaries

- Do **not** change `android:allowBackup` to `false` (decision above).
- Do **not** touch the Room schema, DataStore code, or any Kotlin file.
- Do **not** add encryption to the DB file in this plan.
