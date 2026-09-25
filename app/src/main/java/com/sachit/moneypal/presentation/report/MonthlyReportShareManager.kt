package com.sachit.moneypal.presentation.report

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sachit.moneypal.R
import com.sachit.moneypal.presentation.util.ErrorLogRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shares the generated monthly report PDF (plan 044) via the existing
 * FileProvider (`${applicationId}.fileprovider`) and an `ACTION_SEND` chooser,
 * mirroring the CSV export flow. Failure path: toast only, per plan.
 */
@Singleton
class MonthlyReportShareManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val errorLogRecorder: ErrorLogRecorder,
) {

    /**
     * Fires a share sheet for [file]. Returns true when the sheet was started;
     * a null/error path shows a toast instead (report is kept in cache).
     */
    suspend fun share(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    context.getString(R.string.report_export_subject, file.nameWithoutExtension),
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent.createChooser(shareIntent, null)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            true
        } catch (e: Exception) {
            logcat { "Failed to share monthly report: ${e.asLog()}" }
            errorLogRecorder.record("MonthlyReportShareManager.share", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.report_export_failed, Toast.LENGTH_SHORT).show()
            }
            false
        }
    }
}
