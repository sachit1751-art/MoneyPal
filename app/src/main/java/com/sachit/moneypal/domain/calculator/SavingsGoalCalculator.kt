package com.sachit.moneypal.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Progress toward the user's tracked savings goal (plan 002).
 *
 * @property saved cumulative amount saved across completed months since the
 * goal started (current, incomplete month contributes its partial amount).
 * @property target goal amount from [com.sachit.moneypal.domain.model.SavingsPreferences].
 * @property progressFraction [saved]/[target] clamped to [0, 1].
 * @property estimatedFinishDate month the goal completes at the recent average
 * monthly savings pace; null when no savings have accumulated yet.
 */
data class SavingsGoalProgress(
    val saved: BigDecimal,
    val target: BigDecimal,
    val progressFraction: Float,
    val estimatedFinishDate: LocalDate?,
)

/**
 * Pure calculator for tracked savings-goal progress: cumulative saved amount,
 * progress fraction and an ETA estimated from the recent average monthly
 * savings pace (plan 002). Money math stays on BigDecimal end to end.
 */
class SavingsGoalCalculator @Inject constructor() {

    fun compute(
        monthlyTotals: List<MonthlyTotal>,
        target: BigDecimal?,
        today: LocalDate,
    ): SavingsGoalProgress? {
        if (target == null || target.signum() <= 0) return null

        var saved = BigDecimal.ZERO
        monthlyTotals.forEach { month ->
            saved = saved.add(month.savedAmount.max(BigDecimal.ZERO))
        }

        val fraction = if (saved >= target) {
            1f
        } else {
            saved.divide(target, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
        }

        val eta: LocalDate? = if (saved >= target) {
            null // goal already reached
        } else if (monthlyTotals.isEmpty()) {
            null
        } else {
            val currentMonth = YearMonth.from(today)
            val pastMonths = monthlyTotals.filter { YearMonth.from(it.month).isBefore(currentMonth) }
            val pace: BigDecimal? = if (pastMonths.size >= MIN_PAST_MONTHS_FOR_AVERAGE) {
                pastMonths.map { it.savedAmount.max(BigDecimal.ZERO) }
                    .reduce(BigDecimal::add)
                    .divide(BigDecimal(pastMonths.size), 2, RoundingMode.HALF_UP)
            } else {
                // Not enough history for an average: use the ideal per-period pace.
                null
            }
            val effectivePace = (pace ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
            if (effectivePace.signum() == 0) {
                null
            } else {
                val remaining = target.subtract(saved)
                val monthsNeeded = remaining.divide(effectivePace, 0, RoundingMode.CEILING).toInt()
                today.plusMonths(monthsNeeded.toLong())
            }
        }

        return SavingsGoalProgress(
            saved = saved,
            target = target,
            progressFraction = fraction,
            estimatedFinishDate = eta,
        )
    }

    /** Total saved in one calendar month. */
    data class MonthlyTotal(
        val month: YearMonth,
        val savedAmount: BigDecimal,
    )

    companion object {
        /** Past months required before an average pace is trusted over the ideal pace. */
        const val MIN_PAST_MONTHS_FOR_AVERAGE = 2
    }
}
