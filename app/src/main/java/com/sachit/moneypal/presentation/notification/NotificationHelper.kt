package com.sachit.moneypal.presentation.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sachit.moneypal.R
import com.sachit.moneypal.presentation.MainActivity
import com.sachit.moneypal.presentation.sms.UndoSmsCaptureReceiver
import com.sachit.moneypal.presentation.util.font.format.symbolOnlyCurrencyFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import logcat.logcat
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_PERIOD_END = "budget_period_end"
        const val CHANNEL_RECURRENT = "recurrent_expenses"
        const val CHANNEL_CREDIT = "credit_expenses"

        const val NOTIFICATION_ID_PERIOD_END = 1001
        const val NOTIFICATION_ID_RECURRENT = 1002
        const val NOTIFICATION_ID_CREDIT = 1003
        const val CHANNEL_SMS_CAPTURE = "sms_capture"
        private const val NOTIFICATION_ID_SMS_CAPTURE = 1004
        const val CHANNEL_BUDGET_THRESHOLD = "budget_threshold"
        private const val NOTIFICATION_ID_THRESHOLD_DAILY_80 = 1005
        private const val NOTIFICATION_ID_THRESHOLD_DAILY_100 = 1006
        private const val NOTIFICATION_ID_THRESHOLD_PERIOD_80 = 1007
        private const val NOTIFICATION_ID_THRESHOLD_PERIOD_100 = 1008
        const val CHANNEL_DIGEST = "weekly_digest"
        private const val NOTIFICATION_ID_DIGEST = 1009
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val periodEndChannel = NotificationChannel(
            CHANNEL_PERIOD_END,
            context.getString(R.string.notification_channel_period_end_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_period_end_description)
            enableVibration(true)
        }

        val recurrentChannel = NotificationChannel(
            CHANNEL_RECURRENT,
            context.getString(R.string.notification_channel_recurrent_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_recurrent_description)
            enableVibration(true)
        }

        val creditChannel = NotificationChannel(
            CHANNEL_CREDIT,
            context.getString(R.string.notification_channel_credit_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_credit_description)
            enableVibration(true)
        }

        val smsCaptureChannel = NotificationChannel(
            CHANNEL_SMS_CAPTURE,
            context.getString(R.string.notification_channel_sms_capture_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_sms_capture_description)
            enableVibration(true)
        }

        val thresholdChannel = NotificationChannel(
            CHANNEL_BUDGET_THRESHOLD,
            context.getString(R.string.notification_channel_threshold_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_threshold_description)
            enableVibration(true)
        }

        val digestChannel = NotificationChannel(
            CHANNEL_DIGEST,
            context.getString(R.string.notification_channel_digest_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_digest_description)
        }

        notificationManager.createNotificationChannel(periodEndChannel)
        notificationManager.createNotificationChannel(recurrentChannel)
        notificationManager.createNotificationChannel(creditChannel)
        notificationManager.createNotificationChannel(smsCaptureChannel)
        notificationManager.createNotificationChannel(thresholdChannel)
        notificationManager.createNotificationChannel(digestChannel)
        logcat { "Notification channels created" }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            logcat { "Notification permission (Android 13+): $granted" }
            granted
        } else {
            logcat { "Notification permission: granted (pre-Android 13)" }
            true
        }
    }

    fun showPeriodEndNotification(remainingBudget: String, currency: String) {
        val hasPermission = checkNotificationPermission()
        if (!hasPermission) {
            logcat { "Cannot show notification - permission not granted" }
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = formatAmount(remainingBudget, currency)
        val message = buildPeriodEndMessage(remainingBudget, formattedAmount)

        val notification = NotificationCompat.Builder(context, CHANNEL_PERIOD_END)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_period_end_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addQuickAddAction()
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PERIOD_END, notification)
        logcat { "Period end notification shown successfully" }
    }

    /**
     * Direct-reply "Log expense" action (plan 005). No lock-bypass concern:
     * nothing is displayed in the reply UI, and the entry is written exactly
     * like a manual one.
     */
    private fun NotificationCompat.Builder.addQuickAddAction(): NotificationCompat.Builder {
        val remoteInput = androidx.core.app.RemoteInput.Builder(QuickAddReceiver.KEY_QUICK_ADD_TEXT)
            .setLabel(context.getString(R.string.quick_add_hint))
            .build()
        val quickAddIntent = Intent(context, QuickAddReceiver::class.java).apply {
            action = QuickAddReceiver.ACTION_QUICK_ADD
        }
        val quickAddPending = PendingIntent.getBroadcast(
            context,
            2000,
            quickAddIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(R.string.quick_add_action_label),
                quickAddPending,
            )
                .addRemoteInput(remoteInput)
                .build(),
        )
    }

    /**
     * Weekly digest notification (plan 003): last-7-days spend, top category
     * and remaining budget. Low importance; tap opens the app.
     */
    fun showWeeklyDigest(
        weekTotal: BigDecimal,
        topCategoryName: String?,
        remainingBudget: BigDecimal?,
    ) {
        val hasPermission = checkNotificationPermission()
        if (!hasPermission) {
            logcat { "Cannot show digest notification - permission not granted" }
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_DIGEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currency = "USD"
        val format = symbolOnlyCurrencyFormat(currency)
        val message = if (topCategoryName != null) {
            context.getString(
                R.string.notification_digest_message_with_category,
                format.format(weekTotal),
                topCategoryName,
            )
        } else {
            context.getString(R.string.notification_digest_message, format.format(weekTotal))
        }
        val fullMessage = remainingBudget?.let {
            "$message ${context.getString(R.string.notification_digest_remaining, format.format(it))}"
        } ?: message

        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_digest_title))
            .setContentText(fullMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullMessage))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DIGEST, notification)
        logcat { "Weekly digest notification shown" }
    }

    private fun buildPeriodEndMessage(remainingBudget: String, formattedAmount: String): String {
        val amount = remainingBudget.toDoubleOrNull() ?: 0.0
        return if (amount > 0) {
            context.getString(R.string.notification_period_end_message_positive, formattedAmount)
        } else if (amount < 0) {
            context.getString(
                R.string.notification_period_end_message_negative,
                formattedAmount
            )
        } else {
            context.getString(R.string.notification_period_end_message_neutral)
        }
    }

    private fun formatAmount(amount: String, currency: String): String {
        val decimalValue = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return symbolOnlyCurrencyFormat(currency).format(decimalValue)
    }

    fun showRecurrentExpenseNotification(amount: String, comment: String, currency: String) {
        val hasPermission = checkNotificationPermission()
        if (!hasPermission) {
            logcat { "Cannot show notification - permission not granted" }
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = formatAmount(amount, currency)
        val title = context.getString(R.string.notification_recurrent_expense_title)
        val message = if (comment.isNotBlank()) {
            context.getString(
                R.string.notification_recurrent_expense_message_with_comment,
                comment,
                formattedAmount
            )
        } else {
            context.getString(
                R.string.notification_recurrent_expense_message_without_comment,
                formattedAmount
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_RECURRENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_RECURRENT, notification)
    }

    fun showUpcomingSubscriptionNotification(
        amount: String,
        comment: String,
        daysUntil: Long,
        currency: String
    ) {
        val hasPermission = checkNotificationPermission()
        if (!hasPermission) {
            logcat { "Cannot show notification - permission not granted" }
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val daysText = when (daysUntil) {
            1L -> context.getString(R.string.notification_tomorrow)
            else -> context.getString(R.string.notification_in_days, daysUntil)
        }

        val formattedAmount = formatAmount(amount, currency)
        val title = context.getString(R.string.notification_upcoming_subscription_title)
        val message = if (comment.isNotBlank()) {
            context.getString(
                R.string.notification_upcoming_subscription_message_with_comment,
                comment,
                formattedAmount,
                daysText
            )
        } else {
            context.getString(
                R.string.notification_upcoming_subscription_message_without_comment,
                formattedAmount,
                daysText
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_RECURRENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notificationId = NOTIFICATION_ID_RECURRENT + daysUntil.toInt()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        logcat { "Upcoming subscription notification shown: $message" }
    }

    fun showCreditCutoffNotification(
        totalAmount: String,
        dueDateText: String,
        currency: String
    ) {
        val hasPermission = checkNotificationPermission()
        if (!hasPermission) {
            logcat { "Cannot show credit notification - permission not granted" }
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = formatAmount(totalAmount, currency)
        val message = context.getString(
            R.string.notification_credit_cutoff_message,
            dueDateText,
            formattedAmount
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CREDIT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_credit_cutoff_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CREDIT, notification)
    }

    /**
     * Shows the "captured from bank SMS" notification with an Undo action that
     * deletes the inserted transaction.
     */
    fun showSmsCaptureNotification(
        transactionId: Long,
        amount: String,
        sender: String,
        isCredit: Boolean,
        currency: String,
    ) {
        val hasPermission = checkNotificationPermission()
        if (!hasPermission) {
            logcat { "Cannot show SMS capture notification - permission not granted" }
            return
        }

        val formattedAmount = formatAmount(amount, currency)
        val title = context.getString(
            if (isCredit) R.string.notification_sms_capture_credit_title
            else R.string.notification_sms_capture_debit_title
        )
        val message = context.getString(
            R.string.notification_sms_capture_message,
            formattedAmount,
            sender,
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            4,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val undoIntent = Intent(context, UndoSmsCaptureReceiver::class.java).apply {
            action = UndoSmsCaptureReceiver.ACTION_UNDO
            putExtra(UndoSmsCaptureReceiver.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(UndoSmsCaptureReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID_SMS_CAPTURE)
        }
        val undoPendingIntent = PendingIntent.getBroadcast(
            context,
            transactionId.toInt(),
            undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SMS_CAPTURE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.notification_sms_capture_undo), undoPendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SMS_CAPTURE, notification)
        logcat { "SMS capture notification shown: $message" }
    }

    /**
     * Shows a spending-threshold alert (80% / 100% of daily or period budget).
     * Each threshold/scope combination has a stable notification id so a higher
     * alert replaces the lower one instead of stacking.
     */
    fun showThresholdAlertNotification(
        scope: String,
        thresholdPercent: Int,
        spentFormatted: String,
        budgetFormatted: String,
    ) {
        val hasPermission = checkNotificationPermission()
        if (!hasPermission) {
            logcat { "Cannot show threshold alert - permission not granted" }
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            5,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notification_threshold_title)
        val message = context.getString(
            R.string.notification_threshold_message,
            thresholdPercent,
            scope,
            spentFormatted,
            budgetFormatted,
        )

        val notificationId = when (scope to thresholdPercent) {
            "daily" to 80 -> NOTIFICATION_ID_THRESHOLD_DAILY_80
            "daily" to 100 -> NOTIFICATION_ID_THRESHOLD_DAILY_100
            "period" to 80 -> NOTIFICATION_ID_THRESHOLD_PERIOD_80
            else -> NOTIFICATION_ID_THRESHOLD_PERIOD_100
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_THRESHOLD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        logcat { "Threshold alert shown: scope=$scope percent=$thresholdPercent" }
    }

    fun cancelAllNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
