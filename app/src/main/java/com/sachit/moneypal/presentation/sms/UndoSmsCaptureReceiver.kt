package com.sachit.moneypal.presentation.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import logcat.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Notification "Undo" action for a captured bank SMS: deletes the inserted
 * transaction and dismisses the notification. Runs on goAsync-free scope because
 * Room writes here are single-row deletes (fast, <10ms window is acceptable in
 * practice; the delete is idempotent if the process dies mid-way).
 */
class UndoSmsCaptureReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UNDO) return

        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        NotificationManagerCompat.from(context).cancel(notificationId)

        if (transactionId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                UndoSmsCaptureEntryPointAccess.deleteTransaction(context, transactionId)
                logcat(TAG) { "Undo: deleted SMS transaction $transactionId" }
            } catch (e: Exception) {
                logcat(TAG) { "Undo failed for $transactionId: ${e.message}" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SACHIT:SmsUndo"

        const val ACTION_UNDO = "com.sachit.moneypal.action.UNDO_SMS_CAPTURE"
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
