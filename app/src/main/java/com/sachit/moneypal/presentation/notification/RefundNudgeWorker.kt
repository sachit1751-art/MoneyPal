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

/**
 * Weekly refund nudge (plan 017): reminds the user when expenses flagged
 * `refundExpected` are still unsettled. Disabled by default; enqueued as a
 * unique periodic work request by [RefundNudgeScheduler] when the user opts in
 * (same contract as the weekly digest, plan 003).
 */
class RefundNudgeWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "refund_nudge"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RefundNudgeWorkerEntryPoint {
        fun budgetRepository(): BudgetRepository
        fun settingsRepository(): SettingsRepository
        fun notificationHelper(): NotificationHelper
    }

    override suspend fun doWork(): Result {
        logcat { "RefundNudgeWorker starting..." }
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            RefundNudgeWorkerEntryPoint::class.java,
        )
        val budgetRepository = entryPoint.budgetRepository()
        val settingsRepository = entryPoint.settingsRepository()
        val notificationHelper = entryPoint.notificationHelper()

        if (!settingsRepository.getSettings().refundNudgeEnabled) {
            logcat { "RefundNudgeWorker: disabled, skipping" }
            return Result.success()
        }

        val pending = budgetRepository.observePendingRefunds().first()
        if (pending.isEmpty()) {
            logcat { "RefundNudgeWorker: no pending refunds, skipping" }
            return Result.success()
        }

        notificationHelper.showRefundNudge(pendingCount = pending.size)
        return Result.success()
    }
}
