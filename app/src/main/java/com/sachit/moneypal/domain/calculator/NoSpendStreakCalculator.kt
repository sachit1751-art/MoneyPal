package com.sachit.moneypal.domain.calculator

import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * Result of a no-spend streak scan.
 *
 * @property currentStreakDays consecutive no-spend days ending yesterday or
 * today (today counts only if it has no spend so far).
 * @property longestStreakDays best streak found in the scanned window.
 * @property totalNoSpendDays no-spend days within the scanned window.
 */
data class NoSpendStreak(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val totalNoSpendDays: Int,
)

/**
 * Pure calculator for no-spend streaks: a day is "no-spend" when the sum of
 * its positive (expense) transaction amounts is zero. Income, adjustments and
 * recurring occurrences do not break a streak — only actual spending does.
 */
class NoSpendStreakCalculator @Inject constructor() {

    fun compute(
        transactions: List<com.sachit.moneypal.domain.model.Transaction>,
        today: LocalDate,
        scanWindowDays: Int = DEFAULT_SCAN_WINDOW_DAYS,
    ): NoSpendStreak {
        val spentByDay = HashMap<LocalDate, java.math.BigDecimal>()
        for (transaction in transactions) {
            val amount = transaction.amount
            if (amount.signum() <= 0) continue
            if (transaction.isDeleted || transaction.isAdjustment || transaction.isRecurrent) continue
            val date = transaction.date?.toLocalDate() ?: continue
            spentByDay.merge(date, amount, java.math.BigDecimal::add)
        }

        var currentStreak = 0
        // Walk backwards from today, capped by the scan window. Today counts
        // only while it is still no-spend; otherwise the streak runs up to
        // yesterday.
        var day = today
        val oldest = today.minusDays((scanWindowDays - 1).toLong())
        while (!day.isBefore(oldest) && (spentByDay[day] ?: java.math.BigDecimal.ZERO).signum() == 0) {
            currentStreak++
            day = day.minusDays(1)
        }

        var longest = 0
        var run = 0
        var totalNoSpend = 0
        var cursor = today.minusDays((scanWindowDays - 1).toLong())
        while (!cursor.isAfter(today)) {
            if ((spentByDay[cursor] ?: java.math.BigDecimal.ZERO).signum() == 0) {
                run++
                totalNoSpend++
                if (run > longest) longest = run
            } else {
                run = 0
            }
            cursor = cursor.plusDays(1)
        }

        return NoSpendStreak(
            currentStreakDays = currentStreak,
            longestStreakDays = longest,
            totalNoSpendDays = totalNoSpend,
        )
    }

    companion object {
        const val DEFAULT_SCAN_WINDOW_DAYS = 90
    }
}
