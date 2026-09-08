package com.sachit.moneypal.presentation.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.domain.usecase.ProcessIncomingSmsUseCase
import com.sachit.moneypal.presentation.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import logcat.asLog
import logcat.logcat

/**
 * Parses + inserts a captured bank SMS off the broadcast window. Runs expedited;
 * on failure WorkManager retries with linear backoff (set by [SmsReceiver]).
 */
@HiltWorker
class SmsIngestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processIncomingSmsUseCase: ProcessIncomingSmsUseCase,
    private val budgetRepository: BudgetRepository,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())

        if (body.isBlank()) return Result.success() // nothing to parse; don't retry

        return try {
            when (val result = processIncomingSmsUseCase(sender, body, timestamp)) {
                is ProcessIncomingSmsUseCase.Result.Captured -> {
                    val currency = budgetRepository.getBudgetSettingsSync()?.currencyCode ?: "USD"
                    notificationHelper.showSmsCaptureNotification(
                        transactionId = result.transactionId,
                        amount = result.amount.toPlainString(),
                        sender = result.sender,
                        isCredit = result.isCredit,
                        currency = currency,
                    )
                    Result.success()
                }

                ProcessIncomingSmsUseCase.Result.Ignored -> Result.success()
                is ProcessIncomingSmsUseCase.Result.Error -> {
                    logcat(TAG) { "Ingest error: ${result.reason}" }
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logcat(TAG) { "Ingest crashed\n${e.asLog()}" }
            Result.retry()
        }
    }

    companion object {
        const val KEY_SENDER = "sms_sender"
        const val KEY_BODY = "sms_body"
        const val KEY_TIMESTAMP = "sms_timestamp"
        private const val TAG = "SACHIT:SmsIngest"
    }
}
