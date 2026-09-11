package com.sachit.moneypal.domain.report

import com.sachit.moneypal.domain.model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Scope of a shareable spending report.
 */
enum class ReportScope { DAILY, MONTHLY }

/**
 * Aggregates for one report, computed by [SpendingReportBuilder.build].
 */
data class SpendingReport(
    val scope: ReportScope,
    val periodLabel: String,
    val totalSpent: BigDecimal,
    val totalIncome: BigDecimal,
    val entryCount: Int,
    /** Top categories by spend, name to amount, descending, max 5. */
    val topCategories: List<Pair<String, BigDecimal>>,
    /** Biggest single expense of the scope, or null when there were none. */
    val biggestExpense: Transaction?,
    /** Average spend per day with entries in the scope. */
    val averagePerActiveDay: BigDecimal,
    val dailyBudget: BigDecimal?,
    val currencySymbol: String,
) {
    val isOverDailyBudget: Boolean
        get() = dailyBudget != null && totalSpent > dailyBudget
}

/**
 * Pure builder producing the aggregates for a shareable daily/monthly report.
 * unit-tested in `SpendingReportBuilderTest`; the UI only formats the result.
 */
class SpendingReportBuilder @Inject constructor() {

    fun build(
        scope: ReportScope,
        transactions: List<Transaction>,
        date: LocalDate,
        currencySymbol: String,
        dailyBudget: BigDecimal? = null,
    ): SpendingReport {
        val (start, end) = when (scope) {
            ReportScope.DAILY -> date to date
            ReportScope.MONTHLY -> date.withDayOfMonth(1) to date.withDayOfMonth(date.lengthOfMonth())
        }

        val inScope = transactions.filter { tx ->
            if (tx.isDeleted) return@filter false
            val txDate = tx.date?.toLocalDate() ?: return@filter false
            !txDate.isBefore(start) && !txDate.isAfter(end)
        }
        val spends = inScope.filter { it.amount > BigDecimal.ZERO && !it.isAdjustment }
        val incomes = inScope.filter { it.amount < BigDecimal.ZERO }

        val totalSpent = spends.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }
        val totalIncome = incomes.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount).abs() }

        val categoryTotals = spends
            .groupBy { it.comment.ifBlank { "" } }
            .map { (name, txs) -> name to txs.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) } }
            .sortedByDescending { it.second }
            .take(5)

        val activeDays = spends.mapNotNull { it.date?.toLocalDate() }.distinct().size
        val average = if (activeDays > 0) {
            totalSpent.divide(BigDecimal(activeDays), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val periodLabel = when (scope) {
            ReportScope.DAILY -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))
            ReportScope.MONTHLY -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        }

        return SpendingReport(
            scope = scope,
            periodLabel = periodLabel,
            totalSpent = totalSpent,
            totalIncome = totalIncome,
            entryCount = spends.size,
            topCategories = categoryTotals,
            biggestExpense = spends.maxByOrNull { it.amount },
            averagePerActiveDay = average,
            dailyBudget = dailyBudget,
            currencySymbol = currencySymbol,
        )
    }

    /**
     * Plain-text rendering meant for the Android share sheet (WhatsApp, email,
     * notes). Kept here so the format is identical everywhere and testable.
     */
    fun toShareText(report: SpendingReport, appName: String): String {
        val sb = StringBuilder()
        val title = if (report.scope == ReportScope.DAILY) "Daily report" else "Monthly report"
        sb.appendLine("*$title — ${report.periodLabel}*")
        sb.appendLine()
        sb.appendLine("Spent: ${report.currencySymbol}${report.totalSpent.toPlainString()}")
        if (report.totalIncome > BigDecimal.ZERO) {
            sb.appendLine("Income: ${report.currencySymbol}${report.totalIncome.toPlainString()}")
        }
        report.dailyBudget?.let { budget ->
            val status = if (report.isOverDailyBudget) "⚠️ over" else "✅ within"
            sb.appendLine("Budget: ${report.currencySymbol}${budget.toPlainString()} ($status)")
        }
        sb.appendLine("Entries: ${report.entryCount}")
        if (report.entryCount > 0) {
            sb.appendLine("Avg/day: ${report.currencySymbol}${report.averagePerActiveDay.toPlainString()}")
            report.biggestExpense?.let { biggest ->
                sb.appendLine(
                    "Biggest: ${report.currencySymbol}${biggest.amount.toPlainString()}" +
                        (biggest.comment.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
                )
            }
            if (report.topCategories.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("Top categories:")
                report.topCategories.forEach { (name, amount) ->
                    val label = name.ifBlank { "Uncategorized" }
                    sb.appendLine("• $label: ${report.currencySymbol}${amount.toPlainString()}")
                }
            }
        }
        sb.appendLine()
        sb.appendLine("via $appName")
        return sb.toString().trimEnd()
    }
}
