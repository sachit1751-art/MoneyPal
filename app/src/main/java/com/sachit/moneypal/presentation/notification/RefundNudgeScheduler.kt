package com.sachit.moneypal.presentation.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import logcat.logcat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues/cancels the weekly refund nudge (plan 017). Mirrors
 * [WeeklyDigestScheduler]: disabled by default, never scheduled on install.
 */
@Singleton
class RefundNudgeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    companion object {
        /** Nudge delivery time: Friday 18:00 local (end-of-week money review). */
        val NUDGE_DAY: DayOfWeek = DayOfWeek.FRIDAY
        val NUDGE_TIME: LocalTime = LocalTime.of(18, 0)
    }

    /** Next Friday 18:00 local, strictly in the future. */
    fun nextNudgeTime(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var candidate = now.with(NUDGE_TIME)
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1).with(NUDGE_TIME)
        }
        while (candidate.dayOfWeek != NUDGE_DAY) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    fun reschedule(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(RefundNudgeWorker.WORK_NAME)
            logcat { "RefundNudgeScheduler: cancelled" }
            return
        }
        val initialDelay = java.time.Duration.between(LocalDateTime.now(), nextNudgeTime())
        val request = PeriodicWorkRequestBuilder<RefundNudgeWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay)
            .build()
        workManager.enqueueUniquePeriodicWork(
            RefundNudgeWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        logcat { "RefundNudgeScheduler: enqueued, initial delay $initialDelay" }
    }
}
