package com.sachit.moneypal.domain.report

import com.sachit.moneypal.domain.model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Per-category line of a [MonthlyReportData].
 */
data class MonthlyReportCategory(
    val name: String,
    val total: BigDecimal,
    /** Share of the period's total spend, 0..100 rounded HALF_UP to 1 decimal. */
    val sharePercent: BigDecimal,
)

/**
 * Single biggest-expense line of a [MonthlyReportData].
 */
data class MonthlyReportTopExpense(
    val amount: BigDecimal,
    val comment: String,
    val date: LocalDate,
)

/**
 * Everything the PDF renderer needs for one period (plan 044). Pure data —
 * no Android imports — so the builder is unit-testable on the JVM and the
 * renderer stays a thin typographic layer.
 */
data class MonthlyReportData(
    val periodLabel: String,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val currencyCode: String,
    val totalSpent: BigDecimal,
    val totalIncome: BigDecimal,
    val incomeCount: Int,
    val spendCount: Int,
    val budget: BigDecimal?,
    val categories: List<MonthlyReportCategory>,
    val topExpenses: List<MonthlyReportTopExpense>,
    val noSpendDays: Int,
    val generatedAt: LocalDateTime,
)

/**
 * Pure builder for the shareable period report (plan 044). Consumes the
 * period's transactions plus optional budget; all spend/income math mirrors
 * `BudgetStateCalculator` (income may be negative-amount legacy rows or
 * positive rows with `isIncome = true`; adjustments are neither).
 */
class MonthlyReportBuilder @Inject constructor() {

    fun build(
        transactions: List<Transaction>,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        currencyCode: String,
        budget: BigDecimal? = null,
        generatedAt: LocalDateTime = LocalDateTime.now(),
    ): MonthlyReportData {
        val inScope = transactions.filter { tx ->
            if (tx.isDeleted || tx.isAdjustment) return@filter false
            val txDate = tx.date?.toLocalDate() ?: return@filter false
            !txDate.isBefore(periodStart) && !txDate.isAfter(periodEnd)
        }
        val spends = inScope.filter { it.amount > BigDecimal.ZERO && !it.isIncome }
        val incomes = inScope.filter { it.amount < BigDecimal.ZERO || it.isIncome }

        val totalSpent = spends.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }
        val totalIncome = incomes.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.abs()) }

        val categories = buildCategories(spends, totalSpent)
        // Stable ordering: biggest amount first, ties broken by oldest date.
        val topExpenses = spends
            .sortedWith(
                compareByDescending<Transaction> { it.amount }
                    .thenBy<Transaction> { it.date ?: LocalDateTime.MAX }
            )
            .take(TOP_EXPENSES_LIMIT)
            .map { tx ->
                MonthlyReportTopExpense(
                    amount = tx.amount,
                    comment = tx.comment.ifBlank { "" },
                    date = tx.date?.toLocalDate() ?: periodStart,
                )
            }

        val noSpendDays = generateSequence(periodStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(periodEnd) }
            .count { day -> spends.none { it.date?.toLocalDate() == day } }

        return MonthlyReportData(
            periodLabel = periodStart.format(
                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
            ),
            periodStart = periodStart,
            periodEnd = periodEnd,
            currencyCode = currencyCode,
            totalSpent = totalSpent,
            totalIncome = totalIncome,
            incomeCount = incomes.size,
            spendCount = spends.size,
            budget = budget,
            categories = categories,
            topExpenses = topExpenses,
            noSpendDays = noSpendDays,
            generatedAt = generatedAt,
        )
    }

    /**
     * Ranked category breakdown (max [CATEGORIES_LIMIT]); unnamed groups are
     * aggregated under "Uncategorized" so the share math always covers 100%.
     */
    private fun buildCategories(
        spends: List<Transaction>,
        totalSpent: BigDecimal,
    ): List<MonthlyReportCategory> {
        if (spends.isEmpty() || totalSpent.signum() <= 0) return emptyList()
        val byCategory = spends.groupBy { it.comment.ifBlank { UNCATEGORIZED_LABEL } }
        return byCategory
            .map { (name, txs) ->
                val total = txs.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }
                val share = total
                    .multiply(BigDecimal(100))
                    .divide(totalSpent, 1, RoundingMode.HALF_UP)
                MonthlyReportCategory(name = name, total = total, sharePercent = share)
            }
            .sortedByDescending { it.total }
            .take(CATEGORIES_LIMIT)
    }

    companion object {
        const val CATEGORIES_LIMIT = 8
        const val TOP_EXPENSES_LIMIT = 5
        const val UNCATEGORIZED_LABEL = "Uncategorized"
    }
}
