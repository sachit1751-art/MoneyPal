package com.sachit.moneypal.data.csv

import android.content.Context
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.sachit.moneypal.presentation.util.ErrorLogRecorder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CsvImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val csvService: MinusCsvService,
    private val errorLogRecorder: ErrorLogRecorder,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_INPUT_URI) ?: return Result.failure(
            Data.Builder().putString(KEY_ERROR, "Missing input URI").build()
        )

        return try {
            applicationContext.contentResolver.openInputStream(uriString.toUri()).use { stream ->
                if (stream == null) {
                    return Result.failure(
                        Data.Builder().putString(KEY_ERROR, "Unable to open CSV stream").build()
                    )
                }

                val result = csvService.importTransactions(stream)
                Result.success(
                    Data.Builder()
                        .putInt(KEY_IMPORTED, result.imported)
                        .putInt(KEY_DISCARDED, result.discarded)
                        .putString(KEY_ERROR, result.errors.joinToString("\n"))
                        .build()
                )
            }
        } catch (e: Exception) {
            // Never swallow coroutine cancellation — WorkManager uses it to
            // stop workers (plan 029 worker contract).
            if (e is kotlinx.coroutines.CancellationException) throw e
            errorLogRecorder.record("CsvImportWorker.doWork", e)
            Result.failure(
                Data.Builder().putString(KEY_ERROR, e.message ?: "Import failed").build()
            )
        }
    }

    companion object {
        const val KEY_INPUT_URI = "input_uri"
        const val KEY_IMPORTED = "imported_count"
        const val KEY_DISCARDED = "discarded_count"
        const val KEY_ERROR = "import_error"
    }
}
