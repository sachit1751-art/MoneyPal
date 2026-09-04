package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.PaidRecurrentOccurrence
import com.sachit.moneypal.domain.model.RecurrentFrequency
import com.sachit.moneypal.domain.model.Transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Monthly occurrence day for a billing day [billingDay] in [month], clamping the
 * billing day to the month's length so a 31st is billed on the last day of shorter
 * months (Feb 28/29, Apr 30, ...). Single source of truth shared by the "due today"
 * predicate and the notification scheduler.
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
                !paidOccurrences.contains(PaidRecurrentOccurrence(transaction.id, today))
        }.sumOf { it.amount }
    }

    fun isRecurringDueToday(transaction: Transaction, today: LocalDate): Boolean {
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
}
