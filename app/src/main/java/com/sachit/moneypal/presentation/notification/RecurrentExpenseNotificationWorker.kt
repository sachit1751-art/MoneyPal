package com.sachit.moneypal.presentation.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.calculator.RecurringExpenseCalculator
import com.sachit.moneypal.domain.model.BudgetSettings
import com.sachit.moneypal.domain.model.CreditCard
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.domain.model.calculatePaymentDueDate
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import logcat.asLog
import logcat.logcat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Worker that checks for recurrent expenses that should trigger a new occurrence.
 * This worker runs periodically to check if any recurrent expenses are due today
 * and sends a notification to remind the user.
 * 
 * For monthly subscriptions, uses the specific subscriptionDay (e.g., 15th of each month)
 * rather than calculating from the start date.
 */
class RecurrentExpenseNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "recurrent_expense_notification"
        const val KEY_TRANSACTION_ID = "transaction_id"
        const val TAG_RECURRENT_NOTIFICATION = "recurrent_expense_notification_tag"
        private const val LAST_RECURRENT_NOTIFICATION_PREFIX = "last_recurrent_notification_"
    }
    
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RecurrentExpenseWorkerEntryPoint {
        fun budgetRepository(): BudgetRepository
        fun settingsRepository(): SettingsRepository
        fun notificationHelper(): NotificationHelper
        fun notificationScheduler(): NotificationScheduler
    }

    override suspend fun doWork(): Result {
        logcat { "RecurrentExpenseNotificationWorker starting..." }
        
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            RecurrentExpenseWorkerEntryPoint::class.java
        )
        val budgetRepository = entryPoint.budgetRepository()
        val settingsRepository = entryPoint.settingsRepository()
        val notificationHelper = entryPoint.notificationHelper()
        val notificationScheduler = entryPoint.notificationScheduler()

        return try {
            val settings = budgetRepository.getBudgetSettingsSync()

            if (settings == null) {
                logcat { "No budget settings found, skipping notification" }
                return Result.success()
            }

            val today = LocalDate.now()
            val transactionId = inputData.getLong(KEY_TRANSACTION_ID, 0L)
            if (transactionId > 0L) {
                val transaction = budgetRepository.getTransactionById(transactionId)
                if (transaction == null || transaction.isDeleted || !transaction.isRecurrent) {
                    logcat { "Recurrent notification skipped; transaction missing, deleted, or not recurrent: transactionId=$transactionId" }
                    return Result.success()
                }

                notifyRecurrentTransactionIfDue(
                    transaction = transaction,
                    settings = settings,
                    today = today,
                    notificationHelper = notificationHelper,
                    settingsRepository = settingsRepository,
                    budgetRepository = budgetRepository,
                )
                notificationScheduler.scheduleRecurrentExpenseNotification(transaction)
                return Result.success()
            }

            val transactions = budgetRepository.getTransactions().first()
            maybeSendCreditCutoffReminder(
                settings = settings,
                transactions = transactions,
                today = today,
                notificationHelper = notificationHelper
            )
            logcat { "Legacy recurrent scan completed without sending app-open notifications" }
            Result.success()

        } catch (e: Exception) {
            logcat { "Error in RecurrentExpenseNotificationWorker\n${e.asLog()}" }
            Result.failure()
        }
    }
    private fun maybeSendCreditCutoffReminder(
        settings: BudgetSettings,
        transactions: List<Transaction>,
        today: LocalDate,
        notificationHelper: NotificationHelper
    ) {
        val cutoffDay = settings.creditCardCutoffDay ?: return
        val card = CreditCard(cutoffDay = cutoffDay)
        val dueDate = calculatePaymentDueDate(card, today)

        val daysUntilDueDate = ChronoUnit.DAYS.between(today, dueDate)
        
        // Notify 3 days before the payment due date
        if (daysUntilDueDate != 3L) return

        // The billing period for this due date ended at the corresponding cutoff date.
        val currentMonthCutoff = runCatching { today.withDayOfMonth(cutoffDay) }.getOrElse {
            today.withDayOfMonth(today.lengthOfMonth())
        }
        val actualCutoffDate = if (today.isAfter(currentMonthCutoff)) {
            currentMonthCutoff
        } else {
            currentMonthCutoff.minusMonths(1)
        }

        val creditTotal = transactions
            .filter { tx ->
                tx.isCredit &&
                    !tx.isDeleted &&
                    !tx.isCreditPaid &&
                    tx.date != null &&
                    !tx.date.toLocalDate().isAfter(actualCutoffDate)
            }
            .sumOf { it.amount }

        if (creditTotal <= java.math.BigDecimal.ZERO) return

        val formatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
        notificationHelper.showCreditCutoffNotification(
            totalAmount = creditTotal.toPlainString(),
            dueDateText = dueDate.format(formatter),
            currency = settings.currencyCode
        )
    }

    private suspend fun notifyRecurrentTransactionIfDue(
        transaction: Transaction,
        settings: BudgetSettings,
        today: LocalDate,
        notificationHelper: NotificationHelper,
        settingsRepository: SettingsRepository,
        budgetRepository: BudgetRepository,
    ) {
        if (!RecurringExpenseCalculator().isRecurringDueToday(transaction, today)) {
            logcat { "Recurrent notification worker fired but transaction is not due today: transactionId=${transaction.id} today=$today" }
            return
        }

        val stableId = transaction.sourceTransactionId ?: transaction.id
        val paidDates = budgetRepository.getPaidOccurrenceDatesFor(stableId)
        if (paidDates.contains(today)) {
            logcat { "Recurrent notification worker fired but today's occurrence is already marked paid: transactionId=${transaction.id} today=$today" }
            return
        }

        val dedupeKey = recurrentNotificationDedupeKey(transaction, today)
        val lastNotified = settingsRepository.getString(dedupeKey)
        if (lastNotified == today.toString()) {
            logcat { "Skipping duplicate recurrent notification: transactionId=${transaction.id} date=$today" }
            return
        }

        notificationHelper.showRecurrentExpenseNotification(
            amount = transaction.amount.toPlainString(),
            comment = transaction.comment,
            currency = settings.currencyCode
        )
        settingsRepository.setString(dedupeKey, today.toString())
        logcat { "Recurrent expense notification shown for transactionId=${transaction.id} date=$today" }
    }

    private fun recurrentNotificationDedupeKey(transaction: Transaction, date: LocalDate) =
        "$LAST_RECURRENT_NOTIFICATION_PREFIX${transaction.id}_${date}"

}
