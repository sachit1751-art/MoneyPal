package com.sachit.moneypal.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Result of projecting the period's spending pace forward.
 *
 * @property pacePercent How fast the user is burning the budget relative to
 *   elapsed time, scaled so 100 means perfectly on pace. > 100 means spending
 *   faster than budgeted. Can exceed the displayed range (e.g. 230 = "23%
 *   faster" in UI terms of 130% of pace); UI renders the delta.
 * @property projectedOverspend Positive when the current pace is expected to
 *   exceed the total budget by the end of the period; zero or negative when on
 *   (or under) pace.
 * @property projectedExhaustionDate The date the budget is expected to run out
 *   at the current pace, or null when the pace is within budget (money lasts
 *   through the period end).
 */
data class BurnRateProjection(
    val pacePercent: Int,
    val projectedOverspend: BigDecimal,
    val projectedExhaustionDate: LocalDate?,
)

/**
 * Pure calculator deriving spending-pace statistics from already-computed
 * period aggregates. Kept dependency-free so it is trivially unit-testable.
 */
class BurnRateCalculator @Inject constructor() {

    /**
     * @param totalBudget Effective total budget for the period (income added,
     *   decreases subtracted — the same value `BudgetState.totalBudget` holds).
     * @param spentInPeriod Total spent so far in the period.
     * @param periodStart First day of the budget period (inclusive).
     * @param periodEnd Last day of the budget period (inclusive).
     * @param today The current date; clamped into [periodStart, periodEnd].
     */
    fun calculate(
        totalBudget: BigDecimal,
        spentInPeriod: BigDecimal,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        today: LocalDate,
    ): BurnRateProjection {
        val effectiveToday = when {
            today.isBefore(periodStart) -> periodStart
            today.isAfter(periodEnd) -> periodEnd
            else -> today
        }

        val totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd).toInt() + 1
        val elapsedDays = ChronoUnit.DAYS.between(periodStart, effectiveToday).toInt() + 1

        if (totalBudget <= BigDecimal.ZERO || totalDays <= 0) {
            return BurnRateProjection(
                pacePercent = 0,
                projectedOverspend = BigDecimal.ZERO,
                projectedExhaustionDate = null,
            )
        }

        // Fraction of the period elapsed (>= 1/totalDays once the period started).
        val elapsedFraction = elapsedDays.toBigDecimal()
            .divide(BigDecimal(totalDays), 4, RoundingMode.HALF_UP)

        val pacePercent = spentInPeriod
            .divide(elapsedFraction, 0, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .divide(totalBudget, 0, RoundingMode.HALF_UP)
            .toInt()

        // Whole budget consumed per elapsed day, projected over the whole period.
        val projectedTotalSpend = spentInPeriod
            .multiply(BigDecimal(totalDays))
            .divide(BigDecimal(elapsedDays), 2, RoundingMode.HALF_UP)

        val projectedOverspend = projectedTotalSpend.subtract(totalBudget)
            .max(BigDecimal.ZERO)

        val exhaustionDate = if (spentInPeriod > BigDecimal.ZERO) {
            // Days until the budget runs out at the observed daily burn.
            val dailyBurn = spentInPeriod.divide(BigDecimal(elapsedDays), 4, RoundingMode.HALF_UP)
            if (dailyBurn > BigDecimal.ZERO) {
                val daysLeft = totalBudget
                    .divide(dailyBurn, 0, RoundingMode.DOWN)
                    .toInt()
                val projected = periodStart.plusDays(daysLeft.toLong() - 1)
                if (projected.isBefore(periodEnd) && daysLeft > 0) projected else null
            } else {
                null
            }
        } else {
            null
        }

        return BurnRateProjection(
            pacePercent = pacePercent.coerceAtLeast(0),
            projectedOverspend = projectedOverspend,
            projectedExhaustionDate = exhaustionDate,
        )
    }
}
