package com.sachit.moneypal.presentation.ui.settings.backup

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sachit.moneypal.R
import com.sachit.moneypal.data.backup.BackupCodec
import com.sachit.moneypal.data.backup.BackupFormatException
import com.sachit.moneypal.data.backup.MoneyPalBackup
import com.sachit.moneypal.domain.usecase.CreateBackupUseCase
import com.sachit.moneypal.domain.usecase.RestoreBackupUseCase
import com.sachit.moneypal.presentation.util.ErrorLogRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports and restores full local JSON backups (plan 010). Files go to
 * `Download/MoneyPal-backup-<timestamp>.json` via MediaStore, with a
 * cache-dir + FileProvider fallback. [restoreFrom] reads a user-picked SAF
 * document — no storage permission required.
 */
@Singleton
class BackupTransferManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val createBackupUseCase: CreateBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val errorLogRecorder: ErrorLogRecorder,
) {

    companion object {
        const val MIME_TYPE = "application/json"
        private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
    }

    fun backupFileName(): String =
        "MoneyPal-backup-${LocalDateTime.now().format(TIMESTAMP)}.json"

    suspend fun exportBackup(): Uri? = withContext(Dispatchers.IO) {
        val fileName = backupFileName()
        val payload = BackupCodec.encode(createBackupUseCase())
        saveToDownloads(fileName, payload)
    }

    /**
     * Writes a fresh backup into a user-chosen SAF tree (e.g. a Syncthing or
     * Nextcloud-synced folder), giving the FOSS flavor an off-device,
     * Play-Services-free backup target. Returns true on success.
     */
    suspend fun exportToFolder(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val dirUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri, treeDocId
            )
            val docUri = android.provider.DocumentsContract.createDocument(
                resolver, dirUri, MIME_TYPE, backupFileName()
            ) ?: return@runCatching false
            val payload = BackupCodec.encode(createBackupUseCase())
            resolver.openOutputStream(docUri)?.use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
            } ?: return@runCatching false
            true
        }.onFailure {
            logcat { "Failed to export backup to folder\n${it.asLog()}" }
            errorLogRecorder.record("BackupTransferManager.exportToFolder", it)
        }.getOrDefault(false)
    }

    /**
     * Restores a backup from [uri]. Returns a user-facing result message, or
     * null when the restore failed (message already toasted/logged).
     */
    suspend fun restoreFrom(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: throw BackupFormatException("Could not read the selected file")
            val backup = BackupCodec.decode(raw)
            val result = restoreBackupUseCase(backup)
            context.getString(
                R.string.backup_restore_result,
                result.transactionsRestored,
                result.transactionsSkipped,
                result.categoriesRestored,
            )
        } catch (e: BackupFormatException) {
            errorLogRecorder.record("BackupTransferManager.restoreFrom", e)
            context.getString(R.string.backup_restore_invalid_file)
        } catch (e: Exception) {
            errorLogRecorder.record("BackupTransferManager.restoreFrom", e)
            context.getString(R.string.backup_restore_failed)
        }
    }

    private suspend fun saveToDownloads(fileName: String, payload: String): Uri? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                runCatching {
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let {
                        resolver.openOutputStream(it)?.use { output ->
                            output.write(payload.toByteArray(Charsets.UTF_8))
                        }
                    }
                    uri
                }.onFailure {
                    logcat { "Failed to save backup to Downloads\n${it.asLog()}" }
                    errorLogRecorder.record("BackupTransferManager.saveToDownloads", it)
                }.getOrNull()
            } else {
                // Pre-Q fallback: cache dir + share intent (same pattern as CSV export).
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(exportDir, fileName)
                runCatching {
                    file.writeText(payload, Charsets.UTF_8)
                    val cacheUri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = MIME_TYPE
                        putExtra(Intent.EXTRA_STREAM, cacheUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, fileName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    null
                }.onFailure {
                    errorLogRecorder.record("BackupTransferManager.saveToDownloads(preQ)", it)
                }.getOrNull()
            }
        }

    fun toastSaved() {
        Toast.makeText(context, context.getString(R.string.backup_saved_toast), Toast.LENGTH_LONG).show()
    }
}
