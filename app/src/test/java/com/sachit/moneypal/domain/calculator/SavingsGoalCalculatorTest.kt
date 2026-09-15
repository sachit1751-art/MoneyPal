package com.sachit.moneypal.domain.calculator

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SavingsGoalCalculatorTest {

    private val calculator = SavingsGoalCalculator()

    private fun month(month: YearMonth, saved: String) =
        SavingsGoalCalculator.MonthlyTotal(month, BigDecimal(saved))

    @Test
    fun `returns null when no goal is set`() {
        val progress = calculator.compute(
            monthlyTotals = listOf(month(YearMonth.of(2026, 8), "100")),
            target = null,
            today = LocalDate.of(2026, 9, 15),
        )
        assertThat(progress).isNull()
    }

    @Test
    fun `returns null when goal is zero or negative`() {
        assertThat(
            calculator.compute(emptyList(), BigDecimal.ZERO, LocalDate.of(2026, 9, 15))
        ).isNull()
        assertThat(
            calculator.compute(emptyList(), BigDecimal("-5"), LocalDate.of(2026, 9, 15))
        ).isNull()
    }

    @Test
    fun `zero savings gives zero progress and indeterminate eta`() {
        val progress = calculator.compute(
            monthlyTotals = listOf(month(YearMonth.of(2026, 9), "0")),
            target = BigDecimal("5000"),
            today = LocalDate.of(2026, 9, 15),
        )!!
        assertThat(progress.saved.compareTo(BigDecimal.ZERO)).isEqualTo(0)
        assertThat(progress.progressFraction).isEqualTo(0f)
        assertThat(progress.estimatedFinishDate).isNull()
    }

    @Test
    fun `partial progress computes fraction and eta from average pace`() {
        val progress = calculator.compute(
            monthlyTotals = listOf(
                month(YearMonth.of(2026, 7), "200"),
                month(YearMonth.of(2026, 8), "300"),
                month(YearMonth.of(2026, 9), "100"), // current, partial
            ),
            target = BigDecimal("5000"),
            today = LocalDate.of(2026, 9, 15),
        )!!
        // saved = 600 of 5000
        assertThat(progress.saved.compareTo(BigDecimal("600"))).isEqualTo(0)
        assertThat(progress.progressFraction).isWithin(0.001f).of(0.12f)
        // pace = (200+300)/2 = 250; remaining 4400 -> 18 months
        assertThat(progress.estimatedFinishDate)
            .isEqualTo(LocalDate.of(2026, 9, 15).plusMonths(18))
    }

    @Test
    fun `clamps progress at 100 percent when goal over-achieved`() {
        val progress = calculator.compute(
            monthlyTotals = listOf(
                month(YearMonth.of(2026, 7), "4000"),
                month(YearMonth.of(2026, 8), "3000"),
            ),
            target = BigDecimal("5000"),
            today = LocalDate.of(2026, 9, 15),
        )!!
        assertThat(progress.progressFraction).isEqualTo(1f)
        assertThat(progress.estimatedFinishDate).isNull()
    }

    @Test
    fun `uses ideal pace fallback when history is too short`() {
        // Only the current month exists -> no average; no ideal pace passed
        // (constructor has none), so ETA stays indeterminate.
        val progress = calculator.compute(
            monthlyTotals = listOf(month(YearMonth.of(2026, 9), "50")),
            target = BigDecimal("1000"),
            today = LocalDate.of(2026, 9, 15),
        )!!
        assertThat(progress.estimatedFinishDate).isNull()
    }
}
