package com.sachit.moneypal.domain.report

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.domain.model.Transaction
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Plan 044: the report builder must mirror the app's spend/income split
 * (negative-amount legacy rows or positive `isIncome` rows; adjustments are
 * neither), rank categories with a share that sums to ~100, order top
 * expenses by amount with date-tiebreaks, and handle empty periods.
 */
class MonthlyReportBuilderTest {

    private val builder = MonthlyReportBuilder()

    private val periodStart = LocalDateTime.of(2026, 9, 1, 0, 0)
    private val periodEnd = LocalDateTime.of(2026, 9, 30, 23, 59)

    private fun tx(
        id: Long,
        amount: String,
        comment: String = "",
        day: Int = 5,
        isIncome: Boolean = false,
        isAdjustment: Boolean = false,
        isDeleted: Boolean = false,
    ) = Transaction(
        id = id,
        amount = BigDecimal(amount),
        comment = comment,
        date = LocalDateTime.of(2026, 9, day, 12, 0),
        isIncome = isIncome,
        isAdjustment = isAdjustment,
        isDeleted = isDeleted,
    )

    @Test
    fun `empty period yields a valid report with zero totals`() {
        val report = builder.build(
            transactions = emptyList(),
            periodStart = periodStart.toLocalDate(),
            periodEnd = periodEnd.toLocalDate(),
            currencyCode = "USD",
        )

        assertThat(report.totalSpent).isEqualTo(BigDecimal.ZERO)
        assertThat(report.totalIncome).isEqualTo(BigDecimal.ZERO)
        assertThat(report.categories).isEmpty()
        assertThat(report.topExpenses).isEmpty()
        assertThat(report.spendCount).isEqualTo(0)
        assertThat(report.noSpendDays).isEqualTo(30)
    }

    @Test
    fun `income is excluded from spend totals - both legacy negative and isIncome rows`() {
        val report = builder.build(
            transactions = listOf(
                tx(1, "50.00", "Groceries"),
                tx(2, "-20.00", "Salary"),
                tx(3, "30.00", "Refund", isIncome = true),
            ),
            periodStart = periodStart.toLocalDate(),
            periodEnd = periodEnd.toLocalDate(),
            currencyCode = "USD",
        )

        assertThat(report.totalSpent).isEqualTo(BigDecimal("50.00"))
        assertThat(report.totalIncome).isEqualTo(BigDecimal("50.00"))
        assertThat(report.incomeCount).isEqualTo(2)
        assertThat(report.spendCount).isEqualTo(1)
    }

    @Test
    fun `adjustments and deleted rows are excluded`() {
        val report = builder.build(
            transactions = listOf(
                tx(1, "50.00", "Groceries"),
                tx(2, "10.00", "Fix", isAdjustment = true),
                tx(3, "99.00", "Removed", isDeleted = true),
            ),
            periodStart = periodStart.toLocalDate(),
            periodEnd = periodEnd.toLocalDate(),
            currencyCode = "USD",
        )

        assertThat(report.totalSpent).isEqualTo(BigDecimal("50.00"))
        assertThat(report.spendCount).isEqualTo(1)
    }

    @Test
    fun `category shares sum to approximately 100`() {
        val report = builder.build(
            transactions = listOf(
                tx(1, "40.00", "Food"),
                tx(2, "30.00", "Food"),
                tx(3, "20.00", "Transport"),
                tx(4, "10.00", "Other"),
            ),
            periodStart = periodStart.toLocalDate(),
            periodEnd = periodEnd.toLocalDate(),
            currencyCode = "USD",
        )

        val shareSum = report.categories.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.sharePercent) }
        assertThat(shareSum.toDouble()).isWithin(0.2).of(100.0)
        // Ranked by total, descending.
        assertThat(report.categories.map { it.name }).containsExactly("Food", "Transport", "Other").inOrder()
    }

    @Test
    fun `top expenses are ordered by amount with date tie-breaks and capped at five`() {
        val report = builder.build(
            transactions = listOf(
                tx(1, "10.00", "small", day = 2),
                tx(2, "90.00", "biggest", day = 3),
                tx(3, "50.00", "mid", day = 8),
                tx(4, "50.00", "mid-earlier", day = 6),
                tx(5, "20.00", "tiny", day = 1),
                tx(6, "70.00", "second", day = 4),
                tx(7, "60.00", "third", day = 5),
            ),
            periodStart = periodStart.toLocalDate(),
            periodEnd = periodEnd.toLocalDate(),
            currencyCode = "USD",
        )

        val names = report.topExpenses.map { it.comment }
        assertThat(names).containsExactly("biggest", "second", "third", "mid-earlier", "mid").inOrder()
        // Equal amounts (the two 50s) ordered oldest first.
        assertThat(names).hasSize(5)
        assertThat(names).doesNotContain("small")
        assertThat(names).doesNotContain("tiny")
    }

    @Test
    fun `transactions outside the period are ignored`() {
        val outside = tx(1, "500.00", "Before").copy(
            date = LocalDateTime.of(2026, 8, 15, 12, 0),
        )
        val inside = tx(2, "25.00", "Inside")

        val report = builder.build(
            transactions = listOf(outside, inside),
            periodStart = periodStart.toLocalDate(),
            periodEnd = periodEnd.toLocalDate(),
            currencyCode = "USD",
        )

        assertThat(report.totalSpent).isEqualTo(BigDecimal("25.00"))
    }
}
