package com.sachit.moneypal.presentation.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import logcat.logcat
import java.util.concurrent.TimeUnit

/**
 * Receives SMS_RECEIVED broadcasts (system-protected). Parsing/insertion happens in
 * [SmsIngestWorker] via WorkManager so the broadcast window stays short and the work
 * survives process death. The receiver itself needs no injected dependencies.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        }.getOrNull()
        if (messages.isNullOrEmpty()) return

        val body = messages.joinToString(separator = "") { message ->
            message.displayMessageBody ?: message.messageBody.orEmpty()
        }
        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val timestampMillis = messages.maxOf { it.timestampMillis }

        if (body.isBlank()) {
            logcat(TAG) { "Ignoring blank SMS body from $sender" }
            return
        }

        logcat(TAG) { "SMS received from $sender (${body.length} chars), enqueueing ingest" }
        val request = OneTimeWorkRequestBuilder<SmsIngestWorker>()
            .setInputData(
                workDataOf(
                    SmsIngestWorker.KEY_SENDER to sender,
                    SmsIngestWorker.KEY_BODY to body,
                    SmsIngestWorker.KEY_TIMESTAMP to timestampMillis,
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    companion object {
        private const val TAG = "SACHIT:SmsReceiver"
        private const val WORK_NAME = "sms_ingest"
    }
}
