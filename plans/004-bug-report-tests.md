# 004 — Unit-test zip entry-name sanitization (zip-slip guard) in bug reports

**Audit base:** commit `a2fda9c` — verify with `git rev-parse HEAD` first.

## Why this matters

`BugReportZipGenerator` packages user-chosen attachment URIs (screenshots,
recordings, files) into a zip that is shared via `FileProvider` and emailed to
the maintainer. Zip entry names come from `OpenableColumns.DISPLAY_NAME`,
which is attacker-influenced *only* in the sense that any app the user picks a
file from controls the display name. The sanitizer currently looks correct —
it strips path separators and non-`[A-Za-z0-9._-]` characters — but it is
private and has **zero tests**. A regression here (e.g. a future edit that
reorders sanitize-after-concat, or allows `/`) would produce a classic
zip-slip entry like `../../data/...`, and because the zip is written to the
app's own cache and consumed by email clients/GitHub importers, a malicious
name could escape the archive layout. Lock the behavior down with tests.

## Current state

`app/src/main/java/com/sachit/moneypal/presentation/ui/settings/bugreport/BugReportZipGenerator.kt`,
private instance method (≈lines 191–199):

```kotlin
private fun sanitizeZipEntryName(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
    return normalized
        .replace("[^A-Za-z0-9._-]".toRegex(), "_")
        .trim('_')
}
```

and the caller (≈lines 108–114):

```kotlin
state.selectedAttachmentUris.forEachIndexed { index, uri ->
    val attachmentName = buildAttachmentEntryName(index, uri)
    zipOutput.putNextEntry(ZipEntry("attachments/$attachmentName"))
    ...
```

where `buildAttachmentEntryName` falls back to `attachment_<n>` when the
display name is blank or sanitizes to empty.

## Steps

1. **Make the sanitizer testable without Android.** Change
   `private fun sanitizeZipEntryName(...)` to `internal` and move it (and the
   `queryDisplayName`-independent fallback logic if trivial) onto the
   companion object, or extract it as an `internal` top-level function in the
   same file — do whichever keeps `BugReportZipGenerator` unchanged at call
   sites. Pure JVM tests can then call it directly. No Robolectric needed.

2. **Write `app/src/test/java/com/sachit/moneypal/presentation/ui/settings/bugreport/BugReportZipGeneratorTest.kt`** (JUnit4 + `org.junit.Assert`, matching the repo's existing unit-test style; plain Kotlin, no Android framework):

   Table-driven cases for `sanitizeZipEntryName`:
   - `"vacation.png"` → `"vacation.png"` (unchanged)
   - `"../../evil.png"` → no `/`, `.`, or empty; assert result contains no `/` and no `..`
   - `"a\\b.png"` → no `\`
   - `"café.png"` → `"cafe.png"` (accent stripped via NFD)
   - `"my file (1).png"` → `"my_file_1_.png"` or any output with only `[A-Za-z0-9._-]` (assert with regex, don't over-pin)
   - `"..."` or `"___"` (only stripped chars) → blank (caller then falls back)
   - A 200-char name → length ≤ 200 and regex-clean
   - Assert every result matches `^[A-Za-z0-9._-]*$` and contains neither `/` nor `\` (the zip-slip invariant).

3. **Verify the full zip path can never produce `..`:** assert the invariant
   `ZipEntry("attachments/$name").name` contains no `..` for all cases above
   (this documents the caller's prefixing is safe given a clean leaf name).

4. **Run the new test class:**
   `./gradlew :app:testFossDebugUnitTest --tests "com.sachit.moneypal.presentation.ui.settings.bugreport.BugReportZipGeneratorTest"`
   → expect all green.

5. **Compile gate:** `./gradlew :app:compileFossDebugKotlin` → `BUILD SUCCESSFUL`.

## Done criteria

- [ ] `sanitizeZipEntryName` is `internal` (or the extracted function is) with call sites unchanged.
- [ ] New test file exists with ≥ 8 cases including traversal, backslash, unicode, blank, and length.
- [ ] Test run green; compile gate green.

## Test plan

Covered by the table-driven test above. If detekt is configured to flag long
method/test lines, follow the existing test file conventions (they already
pass detekt) rather than adding suppressions.

## Maintenance note

- If zip handling ever changes (e.g. nested folders, multiple attachments
  with duplicate names — currently two same-named entries are allowed and are
  benign duplicates), re-run this test class.
- `queryDisplayName` still depends on Android `ContentResolver` and stays
  untested; that is acceptable — the security-relevant transform is the
  sanitizer.

## Out of scope / boundaries

- No changes to zip writing, FileProvider config, markdown generation, or the
  bug-report UI flow.
- No changes to `BugReportViewModel`/`BugReportForm` (emailing, permissions).
