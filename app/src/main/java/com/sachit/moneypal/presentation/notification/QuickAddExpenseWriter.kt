package com.sachit.moneypal.presentation.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sachit.moneypal.R
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.model.PaymentMethod
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.domain.usecase.GetCurrentPeriodIdUseCase
import com.sachit.moneypal.presentation.MainActivity
import com.sachit.moneypal.presentation.util.font.format.symbolOnlyCurrencyFormat
import logcat.logcat
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a quick-add attempt (plan 005). */
enum class QuickAddResult { ADDED, QUEUED, FAILED }

/**
 * Writes a quick-add expense using the same rules as manual entry: resolves
 * the active period, queues when past the period end, and reuses
 * find-or-create category semantics (plan 005).
 */
@Singleton
class QuickAddExpenseWriter @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val getCurrentPeriodIdUseCase: GetCurrentPeriodIdUseCase,
) {
    suspend fun quickAdd(amount: BigDecimal, comment: String): QuickAddResult {
        return try {
            val settings = budgetRepository.getBudgetSettingsSync()
            val today = java.time.LocalDate.now()
            val categoryId = if (comment.isNotBlank()) {
                budgetRepository.findOrCreateCategory(comment.trim()).id
            } else null

            val wouldQueue = settings != null && today.isAfter(settings.getPeriodEndDate())
            val tx = Transaction.create(
                amount = amount,
                comment = comment,
                date = LocalDateTime.now(),
                periodId = 0L,
                categoryId = categoryId,
                paymentMethod = PaymentMethod.OTHER,
            )
            if (wouldQueue) {
                budgetRepository.addQueuedTransaction(tx)
                QuickAddResult.QUEUED
            } else {
                val activePeriodId = getCurrentPeriodIdUseCase()
                budgetRepository.addTransactionIfAbsent(
                    tx.copy(periodId = activePeriodId)
                )
                QuickAddResult.ADDED
            }
        } catch (e: Exception) {
            logcat { "QuickAddExpenseWriter failed: ${e.message}" }
            QuickAddResult.FAILED
        }
    }
}

/** Confirmation/error feedback shown in place of the replied notification. */
@Singleton
class QuickAddNotifier @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    fun showResult(amount: BigDecimal, comment: String, result: QuickAddResult) {
        when (result) {
            QuickAddResult.FAILED -> showError(context)
            else -> showConfirmation(context, amount, comment, result)
        }
    }

    companion object {
        fun showError(context: Context) {
            post(context, context.getString(R.string.quick_add_error))
        }

        private fun showConfirmation(
            context: Context,
            amount: BigDecimal,
            comment: String,
            result: QuickAddResult,
        ) {
            val format = symbolOnlyCurrencyFormat("USD")
            val text = if (comment.isBlank()) {
                context.getString(R.string.quick_add_logged_amount, format.format(amount))
            } else {
                context.getString(
                    R.string.quick_add_logged_amount_category,
                    format.format(amount),
                    comment,
                )
            }
            post(
                context,
                if (result == QuickAddResult.QUEUED) {
                    context.getString(R.string.quick_add_queued, text)
                } else {
                    text
                },
            )
        }

        private fun post(context: Context, text: String) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_PERIOD_END)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.quick_add_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(2100, notification)
        }
    }
}
