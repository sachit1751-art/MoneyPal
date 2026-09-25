package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

/**
 * Aggregates for the "Income vs spend" analytics card (plan 046). Percentages
 * are BigDecimal with explicit HALF_UP rounding; a null delta means "unknown"
 * (previous period had zero of that total — the UI renders "—").
 */
data class IncomeSpendComparison(
    val totalIncome: BigDecimal,
    val totalSpend: BigDecimal,
    /** income - spend. */
    val net: BigDecimal,
    /** Income kept as a share of income; null when income is zero. */
    val savingsRatePct: BigDecimal?,
    /** Percent change vs the previous period; null when not computable. */
    val incomeDeltaPct: BigDecimal?,
    val spendDeltaPct: BigDecimal?,
)

/**
 * Pure calculator comparing spend vs income for a period and its predecessor
 * (plan 046). Money classification mirrors `BudgetStateCalculator`/plan 044:
 * spend = amount > 0 && !isIncome && !isAdjustment; income = amount < 0 ||
 * isIncome (adjustments are neither).
 */
class IncomeSpendComparisonCalculator @Inject constructor() {

    fun compute(
        currentPeriodTransactions: List<Transaction>,
        previousPeriodTransactions: List<Transaction>,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): IncomeSpendComparison {
        val current = totals(inWindow(currentPeriodTransactions, periodStart, periodEnd))
        // Like-for-like previous window: same length, immediately before the
        // current period. Callers may pass raw history; the filter keeps the
        // math honest either way.
        val previousLength = java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd) + 1
        val previousStart = periodStart.minusDays(previousLength)
        val previousEnd = periodStart.minusDays(1)
        val previous = totals(inWindow(previousPeriodTransactions, previousStart, previousEnd))

        val net = current.income.subtract(current.spend)

        val savingsRate = if (current.income.signum() > 0) {
            current.income.subtract(current.spend)
                .multiply(ONE_HUNDRED)
                .divide(current.income, 1, RoundingMode.HALF_UP)
        } else {
            null
        }

        return IncomeSpendComparison(
            totalIncome = current.income,
            totalSpend = current.spend,
            net = net,
            savingsRatePct = savingsRate,
            incomeDeltaPct = percentChange(previous.income, current.income),
            spendDeltaPct = percentChange(previous.spend, current.spend),
        )
    }

    private fun inWindow(
        transactions: List<Transaction>,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): List<Transaction> = transactions.filter { tx ->
        val date = tx.date?.toLocalDate() ?: return@filter false
        !date.isBefore(periodStart) && !date.isAfter(periodEnd)
    }

    private fun totals(transactions: List<Transaction>): Totals {
        var income = BigDecimal.ZERO
        var spend = BigDecimal.ZERO
        for (tx in transactions) {
            if (tx.isDeleted || tx.isAdjustment) continue
            when {
                tx.amount < BigDecimal.ZERO || tx.isIncome -> income += tx.amount.abs()
                tx.amount > BigDecimal.ZERO -> spend += tx.amount
            }
        }
        return Totals(income, spend)
    }

    private fun percentChange(previous: BigDecimal, current: BigDecimal): BigDecimal? {
        if (previous.signum() <= 0) return null
        return current.subtract(previous)
            .multiply(ONE_HUNDRED)
            .divide(previous, 1, RoundingMode.HALF_UP)
    }

    private data class Totals(val income: BigDecimal, val spend: BigDecimal)

    private companion object {
        val ONE_HUNDRED = BigDecimal(100)
    }
}
