package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.ArchivedBudget
import com.sachit.moneypal.domain.model.BudgetSettings
import com.sachit.moneypal.domain.model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Shared aggregation for tracked savings-goal progress (plan 022): turns
 * archived periods + the current period's partial savings into monthly saved
 * totals, then delegates the math to [SavingsGoalCalculator].
 *
 * Extracted from AnalyticsViewModel so the widget updater and Analytics share
 * ONE implementation — do not fork this logic per surface.
 */
object SavingsGoalAggregator {

    fun compute(
        archives: List<ArchivedBudget>,
        settings: BudgetSettings?,
        transactions: List<Transaction>,
        savingsPct: Int,
        target: BigDecimal?,
        today: LocalDate,
    ): SavingsGoalProgress? {
        if (target == null || target.signum() <= 0) return null
        val monthlyTotals = LinkedHashMap<YearMonth, BigDecimal>()

        fun accumulate(month: YearMonth, periodBudget: BigDecimal, spent: BigDecimal) {
            val saved = periodBudget.subtract(spent)
                .multiply(BigDecimal(savingsPct))
                .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            monthlyTotals.merge(month, saved, BigDecimal::add)
        }

        for (archive in archives) {
            if (archive.currencyCode != (settings?.currencyCode ?: archive.currencyCode)) continue
            accumulate(YearMonth.from(archive.startDate), archive.totalBudget, archive.spentAmount)
        }

        if (settings != null) {
            val periodSpend = transactions.filter {
                it.amount > BigDecimal.ZERO && !it.isAdjustment && !it.isDeleted
            }.sumOf { it.amount }
            accumulate(YearMonth.from(settings.startDate), settings.totalBudget, periodSpend)
        }

        return SavingsGoalCalculator().compute(
            monthlyTotals = monthlyTotals.map { (month, saved) ->
                SavingsGoalCalculator.MonthlyTotal(month, saved)
            },
            target = target,
            today = today,
        )
    }
}
