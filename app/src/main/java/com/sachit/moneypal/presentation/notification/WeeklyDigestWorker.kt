package com.sachit.moneypal.presentation.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import logcat.logcat
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Weekly digest notification (plan 003): summarizes the last 7 days of spend,
 * the top category, and the remaining budget. Enqueued as a unique periodic
 * work request by [WeeklyDigestScheduler] when the user opts in.
 */
class WeeklyDigestWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "weekly_digest"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WeeklyDigestWorkerEntryPoint {
        fun budgetRepository(): BudgetRepository
        fun settingsRepository(): SettingsRepository
        fun notificationHelper(): NotificationHelper
    }

    override suspend fun doWork(): Result {
        logcat { "WeeklyDigestWorker starting..." }
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WeeklyDigestWorkerEntryPoint::class.java,
        )
        val budgetRepository = entryPoint.budgetRepository()
        val settingsRepository = entryPoint.settingsRepository()
        val notificationHelper = entryPoint.notificationHelper()

        val settings = settingsRepository.getSettings()
        if (!settings.weeklyDigestEnabled) {
            logcat { "WeeklyDigestWorker: disabled, skipping" }
            return Result.success()
        }

        val weekAgo = LocalDate.now().minusDays(7)

        val transactions = budgetRepository.getTransactions().first()
        val weekTx = transactions.filter { tx ->
            !tx.isDeleted && tx.amount.signum() > 0 &&
                tx.date != null && !tx.date.isBefore(weekAgo.atTime(23, 59))
        }
        val weekTotal = weekTx.sumOf { it.amount }
        val topCategoryName = weekTx.groupBy { it.categoryId }
            .filterKeys { it != null }
            .maxByOrNull { entry -> entry.value.sumOf { it.amount } }
            ?.key
            ?.let { id -> budgetRepository.getAllCategories().first().find { it.id == id }?.name }

        val budgetSettings = budgetRepository.getBudgetSettings().first()
        val remaining = budgetSettings?.let {
            val spent = transactions.filter { tx ->
                !tx.isDeleted && tx.amount.signum() > 0 && tx.date != null &&
                    !tx.date.isBefore(budgetSettings.startDate.atStartOfDay())
            }.sumOf { it.amount }
            it.totalBudget.subtract(spent).max(BigDecimal.ZERO)
        }

        notificationHelper.showWeeklyDigest(
            weekTotal = weekTotal,
            topCategoryName = topCategoryName,
            remainingBudget = remaining,
        )
        return Result.success()
    }
}
