package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.PaidRecurrentOccurrence
import com.sachit.moneypal.domain.model.RecurrentFrequency
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.domain.model.containsOccurrence
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Monthly occurrence day for a billing day [billingDay] in [month], clamping the
 * billing day to the month's length so a 31st is billed on the last day of shorter
 * months (Feb 28/29, Apr 30, ...). Single source of truth shared by the "due today"
 * predicate, [nextOccurrenceDate] and the notification scheduler.
 */
internal fun monthlyOccurrenceDay(billingDay: Int, month: YearMonth): Int =
    billingDay.coerceIn(1, month.lengthOfMonth())

class RecurringExpenseCalculator @Inject constructor() {

    fun calculateRecurringDueToday(
        transactions: List<Transaction>,
        today: LocalDate,
        paidOccurrences: Set<PaidRecurrentOccurrence> = emptySet(),
    ): BigDecimal {
        val recurrentTransactions = transactions.filter { it.isRecurrent && !it.isDeleted }
        return recurrentTransactions.filter { transaction ->
            isRecurringDueToday(transaction, today) &&
                !paidOccurrences.containsOccurrence(transaction.id, today)
        }.sumOf { it.amount }
    }

    fun isRecurringDueToday(transaction: Transaction, today: LocalDate): Boolean {
        // Plan 008: paused recurrings never come due (reminders and due-today
        // sums stay off until the user resumes).
        if (transaction.isRecurrentPaused) return false

        val frequency = transaction.recurrentFrequency ?: return false
        val startDate = transaction.date?.toLocalDate() ?: return false

        val endDate = transaction.recurrentEndDate?.toLocalDate()
        if (endDate != null && today.isAfter(endDate)) {
            return false
        }

        if (today.isBefore(startDate)) {
            return false
        }

        return when (frequency) {
            RecurrentFrequency.WEEKLY -> {
                val daysBetween = ChronoUnit.DAYS.between(startDate, today).toInt()
                daysBetween >= 0 && daysBetween % 7 == 0
            }

            RecurrentFrequency.BIWEEKLY -> {
                val daysBetween = ChronoUnit.DAYS.between(startDate, today).toInt()
                daysBetween >= 0 && daysBetween % 14 == 0
            }

            RecurrentFrequency.MONTHLY -> {
                val billingDay = transaction.subscriptionDay ?: startDate.dayOfMonth
                today.dayOfMonth == monthlyOccurrenceDay(billingDay, YearMonth.from(today))
            }
        }
    }

    /**
     * Next occurrence strictly after [today] (plan 008). Returns null when the
     * recurrence has ended or the transaction is not recurring. Clamps monthly
     * billing days to the target month's length, matching [isRecurringDueToday].
     */
    fun nextOccurrenceDate(transaction: Transaction, today: LocalDate): LocalDate? {
        if (!transaction.isRecurrent) return null
        val frequency = transaction.recurrentFrequency ?: return null
        val startDate = transaction.date?.toLocalDate() ?: return null
        val endDate = transaction.recurrentEndDate?.toLocalDate()

        val next: LocalDate = when (frequency) {
            RecurrentFrequency.WEEKLY -> {
                var candidate = startDate
                while (!candidate.isAfter(today)) candidate = candidate.plusWeeks(1)
                candidate
            }

            RecurrentFrequency.BIWEEKLY -> {
                var candidate = startDate
                while (!candidate.isAfter(today)) candidate = candidate.plusWeeks(2)
                candidate
            }

            RecurrentFrequency.MONTHLY -> {
                val billingDay = transaction.subscriptionDay ?: startDate.dayOfMonth
                var month = YearMonth.from(startDate)
                var candidate = startDate
                while (!candidate.isAfter(today)) {
                    month = month.plusMonths(1)
                    candidate = month.atDay(monthlyOccurrenceDay(billingDay, month))
                }
                candidate
            }
        }

        if (endDate != null && next.isAfter(endDate)) return null
        return next
    }
}
