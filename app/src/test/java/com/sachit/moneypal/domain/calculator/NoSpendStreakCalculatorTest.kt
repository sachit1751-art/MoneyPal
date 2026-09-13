package com.sachit.moneypal.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.domain.model.Transaction
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class NoSpendStreakCalculatorTest {

    private val calculator = NoSpendStreakCalculator()

    private fun tx(
        amount: String,
        day: Int,
        isAdjustment: Boolean = false,
        isRecurrent: Boolean = false,
    ) = Transaction(
        id = 0L,
        amount = BigDecimal(amount),
        comment = "",
        date = LocalDateTime.of(2026, 9, day, 12, 0),
        isAdjustment = isAdjustment,
        isRecurrent = isRecurrent,
    )

    @Test
    fun `no transactions means streak equals window`() {
        val streak = calculator.compute(
            transactions = emptyList(),
            today = LocalDateTime.of(2026, 9, 15, 0, 0).toLocalDate(),
        )
        assertThat(streak.currentStreakDays).isEqualTo(NoSpendStreakCalculator.DEFAULT_SCAN_WINDOW_DAYS)
        assertThat(streak.totalNoSpendDays).isEqualTo(NoSpendStreakCalculator.DEFAULT_SCAN_WINDOW_DAYS)
    }

    @Test
    fun `today with spend breaks streak at yesterday`() {
        val transactions = listOf(tx("10.00", 15))
        val streak = calculator.compute(transactions, today = LocalDateTime.of(2026, 9, 15, 0, 0).toLocalDate())
        assertThat(streak.currentStreakDays).isEqualTo(0)
    }

    @Test
    fun `streak counts back from today across spend-free days`() {
        val transactions = listOf(
            tx("10.00", 13), // spend three days ago
            tx("10.00", 10),
        )
        val streak = calculator.compute(
            transactions = transactions,
            today = LocalDateTime.of(2026, 9, 15, 0, 0).toLocalDate(),
            scanWindowDays = 6, // Sep 10..15
        )
        // 15, 14 are no-spend; 13 has spend.
        assertThat(streak.currentStreakDays).isEqualTo(2)
        assertThat(streak.longestStreakDays).isEqualTo(2) // 11, 12 and 14, 15
    }

    @Test
    fun `income adjustments and recurring do not break a streak`() {
        val transactions = listOf(
            tx("-500.00", 14), // income
            tx("10.00", 14, isAdjustment = true), // decrease
            tx("10.00", 13, isRecurrent = true), // recurrent occurrence
            tx("25.00", 11), // real spend
        )
        val streak = calculator.compute(transactions, today = LocalDateTime.of(2026, 9, 15, 0, 0).toLocalDate())
        // 15, 14, 13, 12 are spend-free (11 has the real spend).
        assertThat(streak.currentStreakDays).isEqualTo(4)
    }

    @Test
    fun `longest streak may exceed current streak`() {
        val transactions = listOf(
            tx("10.00", 15), // today breaks the current run
            tx("10.00", 12),
            tx("10.00", 8),
        )
        val streak = calculator.compute(
            transactions = transactions,
            today = LocalDateTime.of(2026, 9, 15, 0, 0).toLocalDate(),
            scanWindowDays = 8, // Sep 8..15
        )
        assertThat(streak.currentStreakDays).isEqualTo(0)
        // Runs: 9,10,11 (between spends on 8 and 12).
        assertThat(streak.longestStreakDays).isEqualTo(3)
    }

    @Test
    fun `window caps computation`() {
        val streak = calculator.compute(
            transactions = emptyList(),
            today = LocalDateTime.of(2026, 9, 15, 0, 0).toLocalDate(),
            scanWindowDays = 5,
        )
        assertThat(streak.currentStreakDays).isEqualTo(5)
        assertThat(streak.totalNoSpendDays).isEqualTo(5)
    }
}
