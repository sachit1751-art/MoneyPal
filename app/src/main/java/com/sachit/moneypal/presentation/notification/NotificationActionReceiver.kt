package com.sachit.moneypal.presentation.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import logcat.logcat
import java.time.LocalDate

/**
 * Notification action buttons on recurring-payment reminders (plan 045):
 * "mark paid" settles the occurrence through the same path as the in-app
 * button and dismisses the notification; "snooze" re-fires the reminder in
 * one day without touching occurrence state.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationActionEntryPoint {
        fun notificationActionHandler(): NotificationActionHandler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        val occurrenceDateEpochDay = intent.getLongExtra(EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (transactionId <= 0L || occurrenceDateEpochDay < 0L) return

        val action = intent.action
        if (action != ACTION_MARK_OCCURRENCE_PAID && action != ACTION_SNOOZE_OCCURRENCE) return

        // Actions dismiss their notification immediately; the mark-paid path
        // re-derives state from the DB when the handler runs.
        NotificationManagerCompat.from(context).cancel(notificationId)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val handler = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    NotificationActionEntryPoint::class.java,
                ).notificationActionHandler()
                val handled = when (action) {
                    ACTION_MARK_OCCURRENCE_PAID -> handler.markOccurrencePaid(
                        transactionId = transactionId,
                        occurrenceDate = LocalDate.ofEpochDay(occurrenceDateEpochDay),
                    )
                    else -> handler.snoozeOccurrence(transactionId)
                }
                if (!handled) {
                    logcat(TAG) { "Action $action ignored: transactionId=$transactionId unknown or deleted" }
                }
            } catch (e: Exception) {
                logcat(TAG) { "Action $action failed for transactionId=$transactionId: ${e.message}" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SACHIT:NotifActions"

        const val ACTION_MARK_OCCURRENCE_PAID =
            "com.sachit.moneypal.action.MARK_OCCURRENCE_PAID"
        const val ACTION_SNOOZE_OCCURRENCE =
            "com.sachit.moneypal.action.SNOOZE_OCCURRENCE"
        const val EXTRA_TRANSACTION_ID = "extra_occurrence_transaction_id"
        const val EXTRA_OCCURRENCE_DATE_EPOCH_DAY = "extra_occurrence_date_epoch_day"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
