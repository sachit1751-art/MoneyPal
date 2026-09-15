package com.sachit.moneypal.presentation.notification

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sachit.moneypal.data.backup.BackupCodec
import com.sachit.moneypal.domain.usecase.CreateBackupUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import logcat.logcat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in automatic backup into a user-picked SAF folder (plan 004): 15-day
 * cadence, keeps the newest [MAX_KEPT_BACKUPS] files, silently no-ops when
 * disabled or the folder grant was revoked.
 */
@Singleton
class AutoBackupScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val createBackupUseCase: CreateBackupUseCase,
    private val settingsRepository: com.sachit.moneypal.data.repository.SettingsRepository,
) {
    private val backupCodec = com.sachit.moneypal.data.backup.BackupCodec
    companion object {
        const val CADENCE_DAYS = 15L
        const val MAX_KEPT_BACKUPS = 3
        private const val MIME_JSON = "application/json"
    }

    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    /** Pure-ish decision: should a backup run now? Unit-tested. */
    fun shouldRunNow(enabled: Boolean, treeUri: String, lastRunAt: Long, now: Instant): Boolean {
        if (!enabled || treeUri.isBlank()) return false
        if (lastRunAt <= 0L) return true
        return Duration.between(Instant.ofEpochMilli(lastRunAt), now).toDays() >= CADENCE_DAYS
    }

    /** Runs a backup immediately if due; returns true when a backup succeeded. */
    suspend fun runNow(): Boolean {
        val settings = settingsRepository.observeSettings().first()
        if (!shouldRunNow(
                enabled = settings.autoBackupEnabled,
                treeUri = settings.autoBackupTreeUri,
                lastRunAt = settings.autoBackupLastRunAt,
                now = Instant.now(),
            )
        ) {
            return false
        }
        return writeBackup(settings.autoBackupTreeUri)
    }

    suspend fun forceRun(): Boolean {
        val settings = settingsRepository.observeSettings().first()
        if (settings.autoBackupTreeUri.isBlank()) return false
        return writeBackup(settings.autoBackupTreeUri)
    }

    private suspend fun writeBackup(treeUriString: String): Boolean {
        return try {
            val treeUri = Uri.parse(treeUriString)
            val backup = createBackupUseCase()
            val json = backupCodec.encode(backup)
            val today = LocalDate.now()
            val fileName = "moneypal-backup-$today.json"

            val docUri = DocumentsContract.createDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                ),
                MIME_JSON,
                fileName,
            ) ?: return false

            context.contentResolver.openOutputStream(docUri)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            } ?: return false

            pruneOldBackups(treeUri, keep = MAX_KEPT_BACKUPS - 1)
            settingsRepository.setAutoBackupLastRunAt(System.currentTimeMillis())
            logcat { "AutoBackup: wrote $fileName" }
            true
        } catch (e: Exception) {
            logcat { "AutoBackup failed: ${e.message}" }
            false
        }
    }

    /** Deletes the oldest backup documents beyond [keep]. Best-effort. */
    private fun pruneOldBackups(treeUri: Uri, keep: Int) {
        try {
            val children = context.contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                ),
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )
            children?.use { cursor ->
                val backups = mutableListOf<Pair<String, String>>()
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: ""
                    if (name.startsWith("moneypal-backup-") && name.endsWith(".json")) {
                        backups.add(id to name)
                    }
                }
                backups.sortBy { it.second }
                backups.dropLast(keep).forEach { (id, _) ->
                    runCatching {
                        DocumentsContract.deleteDocument(
                            context.contentResolver,
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logcat { "AutoBackup prune failed: ${e.message}" }
        }
    }

    fun reschedule(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(AutoBackupWorker.WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            CADENCE_DAYS, TimeUnit.DAYS,
        ).build()
        workManager.enqueueUniquePeriodicWork(
            AutoBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
