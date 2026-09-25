package com.sachit.moneypal.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.domain.model.Transaction
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Plan 046: income/spend comparison — totals split, savings-rate rounding,
 * zero-previous guards (delta null), empty periods.
 */
class IncomeSpendComparisonCalculatorTest {

    private val calculator = IncomeSpendComparisonCalculator()

    private val start = LocalDate.of(2026, 9, 1)
    private val end = LocalDate.of(2026, 9, 30)

    private fun tx(
        id: Long,
        amount: String,
        day: Int = 5,
        month: Int = 9,
        isIncome: Boolean = false,
        isAdjustment: Boolean = false,
    ) = Transaction(
        id = id,
        amount = BigDecimal(amount),
        comment = "",
        date = LocalDateTime.of(2026, month, day, 12, 0),
        isIncome = isIncome,
        isAdjustment = isAdjustment,
    )

    private fun compute(
        current: List<Transaction>,
        previous: List<Transaction> = emptyList(),
    ) = calculator.compute(current, previous, start, end)

    @Test
    fun `empty period yields zero totals and null percentages`() {
        val result = compute(emptyList())

        assertThat(result.totalIncome.compareTo(BigDecimal.ZERO)).isEqualTo(0)
        assertThat(result.totalSpend.compareTo(BigDecimal.ZERO)).isEqualTo(0)
        assertThat(result.savingsRatePct).isNull()
        assertThat(result.incomeDeltaPct).isNull()
        assertThat(result.spendDeltaPct).isNull()
    }

    @Test
    fun `spend only period - savings rate undefined and deltas null`() {
        val result = compute(
            current = listOf(tx(1, "40.00"), tx(2, "60.00")),
        )

        assertThat(result.totalSpend.compareTo(BigDecimal("100.00"))).isEqualTo(0)
        // Savings rate is only defined when income > 0.
        assertThat(result.savingsRatePct).isNull()
        assertThat(result.incomeDeltaPct).isNull()
        assertThat(result.spendDeltaPct).isNull()
    }

    @Test
    fun `income only period - full savings rate`() {
        val result = compute(
            current = listOf(tx(1, "100.00", isIncome = true)),
        )

        assertThat(result.savingsRatePct!!.compareTo(BigDecimal("100.0"))).isEqualTo(0)
        assertThat(result.net.compareTo(BigDecimal("100.00"))).isEqualTo(0)
    }

    @Test
    fun `mixed period computes net and savings rate`() {
        val result = compute(
            current = listOf(
                tx(1, "200.00", isIncome = true),
                tx(2, "150.00"),
            ),
        )

        assertThat(result.net.compareTo(BigDecimal("50.00"))).isEqualTo(0)
        assertThat(result.savingsRatePct!!.compareTo(BigDecimal("25.0"))).isEqualTo(0)
    }

    @Test
    fun `deltas computed against previous period`() {
        val result = compute(
            current = listOf(tx(1, "200.00", isIncome = true), tx(2, "100.00")),
            previous = listOf(
                tx(3, "100.00", isIncome = true, day = 10, month = 8),
                tx(4, "100.00", day = 15, month = 8),
            ),
        )

        // Income doubled: +100%; spend unchanged: 0%.
        assertThat(result.incomeDeltaPct!!.compareTo(BigDecimal("100.0"))).isEqualTo(0)
        assertThat(result.spendDeltaPct!!.compareTo(BigDecimal("0.0"))).isEqualTo(0)
    }

    @Test
    fun `previous period transactions outside the window are ignored`() {
        val result = compute(
            current = listOf(tx(1, "100.00")),
            previous = listOf(
                // July row: outside the like-for-like previous window
                // (Aug 2 .. Aug 31 for a 30-day September period).
                tx(2, "500.00", day = 5, month = 7),
                tx(3, "50.00", day = 20, month = 8),
            ),
        )

        assertThat(result.spendDeltaPct!!.compareTo(BigDecimal("100.0"))).isEqualTo(0)
    }

    @Test
    fun `adjustments are excluded from both totals`() {
        val result = compute(
            current = listOf(tx(1, "100.00"), tx(2, "75.00", isAdjustment = true)),
        )

        assertThat(result.totalSpend.compareTo(BigDecimal("100.00"))).isEqualTo(0)
    }
}
