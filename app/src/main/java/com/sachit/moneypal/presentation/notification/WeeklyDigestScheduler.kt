package com.sachit.moneypal.presentation.notification

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sachit.moneypal.R
import com.sachit.moneypal.presentation.MainActivity
import com.sachit.moneypal.presentation.util.font.format.symbolOnlyCurrencyFormat
import logcat.logcat
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues/cancels the weekly digest periodic work and posts the digest
 * notification (plan 003).
 */
@Singleton
class WeeklyDigestScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    private val workManager: androidx.work.WorkManager by lazy {
        androidx.work.WorkManager.getInstance(context)
    }
    private val notificationHelper: NotificationHelper by lazy {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context,
            DigestNotificationEntryPoint::class.java,
        ).notificationHelper()
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface DigestNotificationEntryPoint {
        fun notificationHelper(): NotificationHelper
    }
    companion object {
        /** Digest delivery time: Monday 09:00 local. */
        val DIGEST_DAY: DayOfWeek = DayOfWeek.MONDAY
        val DIGEST_TIME: LocalTime = LocalTime.of(9, 0)
        private const val INITIAL_DELAY_DAYS_TOLERANCE = 8L
    }

    /** Next Monday 09:00 local, strictly in the future. */
    fun nextDigestTime(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var candidate = now.with(DIGEST_TIME)
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1).with(DIGEST_TIME)
        }
        while (candidate.dayOfWeek != DIGEST_DAY) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    fun reschedule(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(WeeklyDigestWorker.WORK_NAME)
            logcat { "WeeklyDigestScheduler: cancelled" }
            return
        }
        val initialDelay = java.time.Duration.between(
            LocalDateTime.now(),
            nextDigestTime(),
        )
        val request = androidx.work.PeriodicWorkRequestBuilder<WeeklyDigestWorker>(
            7, java.util.concurrent.TimeUnit.DAYS,
        )
            .setInitialDelay(initialDelay)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WeeklyDigestWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        logcat { "WeeklyDigestScheduler: enqueued, initial delay $initialDelay" }
    }

    /**
     * Posts the digest notification; [remainingBudget] may be null when no
     * budget is configured.
     */
    fun showDigest(
        weekTotal: BigDecimal,
        topCategoryName: String?,
        remainingBudget: BigDecimal?,
    ) {
        notificationHelper.showWeeklyDigest(
            weekTotal = weekTotal,
            topCategoryName = topCategoryName,
            remainingBudget = remainingBudget,
        )
    }
}
