package com.sachit.moneypal.presentation.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sachit.moneypal.data.repository.BudgetRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.asLog
import logcat.logcat
import java.time.LocalDate

class PeriodEndAlarmReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PeriodEndAlarmReceiverEntryPoint {
        fun budgetRepository(): BudgetRepository
        fun notificationHelper(): NotificationHelper
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationScheduler.ACTION_SHOW_PERIOD_END_NOTIFICATION) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    PeriodEndAlarmReceiverEntryPoint::class.java
                )
                handlePeriodEndAlarm(
                    budgetRepository = entryPoint.budgetRepository(),
                    notificationHelper = entryPoint.notificationHelper(),
                    today = LocalDate.now(),
                )
            } catch (e: Exception) {
                logcat { "Error handling period end alarm\n${e.asLog()}" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal suspend fun handlePeriodEndAlarm(
        budgetRepository: BudgetRepository,
        notificationHelper: NotificationHelper,
        today: LocalDate,
    ) {
        val settings = budgetRepository.getBudgetSettingsSync() ?: return
        val periodEnd = settings.getPeriodEndDate()
        if (!today.isAfter(periodEnd)) {
            return
        }
        val transactions = budgetRepository.getTransactions().first()
        val periodTransactions = transactions.filter { transaction ->
            val txDate = transaction.date?.toLocalDate()
            txDate != null && !txDate.isBefore(settings.startDate) && !txDate.isAfter(
                periodEnd
            )
        }
        val totalSpent = periodTransactions
            .filter { !it.isDeleted }
            .sumOf { it.amount }
        val remaining = settings.totalBudget.subtract(totalSpent)
        notificationHelper.showPeriodEndNotification(
            remainingBudget = remaining.toPlainString(),
            currency = settings.currencyCode
        )
    }
}
