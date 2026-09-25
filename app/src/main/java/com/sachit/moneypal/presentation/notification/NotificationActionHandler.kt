package com.sachit.moneypal.presentation.notification

import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.domain.usecase.GetCurrentPeriodIdUseCase
import com.sachit.moneypal.presentation.ui.budget.BudgetTransactionHandler
import kotlinx.coroutines.flow.first
import logcat.logcat
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notification action back-end (plan 045): settles ("mark paid") or snoozes a
 * recurring occurrence straight from the reminder notification. Fail-silent on
 * unknown input per the plan-029 background error contract.
 */
@Singleton
class NotificationActionHandler @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val budgetTransactionHandler: BudgetTransactionHandler,
    private val getCurrentPeriodIdUseCase: GetCurrentPeriodIdUseCase,
    private val notificationScheduler: NotificationScheduler,
) {
    companion object {
        /** How long a snoozed reminder waits before firing again (plan 045). */
        const val SNOOZE_DELAY_DAYS = 1L

        private const val TAG = "SACHIT:NotifActions"
    }

    /**
     * Marks the occurrence of [transactionId] on [occurrenceDate] paid using
     * the same path as the in-app mark-paid button (occurrence recorded, expense
     * booked, reminder rescheduled). Returns false when the transaction no
     * longer exists (notification from a stale row) — nothing is written.
     */
    suspend fun markOccurrencePaid(transactionId: Long, occurrenceDate: LocalDate): Boolean {
        val template = resolveRecurring(transactionId) ?: return false
        val stableId = template.sourceTransactionId ?: template.id
        val activePeriodId = getCurrentPeriodIdUseCase()
        val result = budgetTransactionHandler.markRecurrentOccurrencePaid(
            transaction = template,
            activePeriodId = activePeriodId,
            occurrenceDate = occurrenceDate,
        )
        if (result.isFailure) {
            logcat { "Mark-paid action failed for stableId=$stableId occurrence=$occurrenceDate" }
            return false
        }
        logcat { "Mark-paid action settled stableId=$stableId occurrence=$occurrenceDate" }
        return true
    }

    /**
     * Snoozes the reminder for [transactionId] by [delayDays] (default 1 day).
     * Never touches occurrence state: paid/skip records and the next-cycle
     * schedule stay exactly as they are. Returns false when the transaction no
     * longer exists.
     */
    suspend fun snoozeOccurrence(transactionId: Long, delayDays: Long = SNOOZE_DELAY_DAYS): Boolean {
        val template = resolveRecurring(transactionId) ?: return false
        notificationScheduler.snoozeRecurrentExpenseNotification(template, delayDays)
        logcat { "Snoozed reminder for transactionId=${template.id} by ${delayDays}d" }
        return true
    }

    /** Resolves a recurring row by id or stable id; null when unknown/deleted. */
    private suspend fun resolveRecurring(transactionId: Long): Transaction? {
        val byId = budgetRepository.getTransactionById(transactionId)
        if (byId != null && byId.isRecurrent && !byId.isDeleted) return byId
        return budgetRepository.getTransactions().first().firstOrNull {
            (it.id == transactionId || it.sourceTransactionId == transactionId) &&
                it.isRecurrent && !it.isDeleted
        }
    }
}