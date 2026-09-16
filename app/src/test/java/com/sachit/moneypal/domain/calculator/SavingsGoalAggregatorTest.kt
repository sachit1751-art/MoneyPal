package com.sachit.moneypal.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.domain.model.ArchivedBudget
import com.sachit.moneypal.domain.model.BudgetPeriod
import com.sachit.moneypal.domain.model.BudgetSettings
import com.sachit.moneypal.domain.model.Transaction
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tests for the shared savings-goal aggregation (plan 022) feeding both the
 * Analytics screen and the home-screen widget.
 */
class SavingsGoalAggregatorTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 17)

    private fun settings(
        totalBudget: BigDecimal,
        startDate: LocalDate = LocalDate.of(2026, 9, 1),
        currencyCode: String = "USD",
    ) = BudgetSettings(
        totalBudget = totalBudget,
        period = BudgetPeriod.MONTHLY,
        startDate = startDate,
        currencyCode = currencyCode,
    )

    private fun archive(
        startDate: LocalDate,
        totalBudget: BigDecimal,
        spentAmount: BigDecimal,
        currencyCode: String = "USD",
    ) = ArchivedBudget(
        periodId = startDate.toEpochDay(),
        totalBudget = totalBudget,
        spentAmount = spentAmount,
        startDate = startDate,
        endDate = startDate.plusMonths(1).minusDays(1),
        currencyCode = currencyCode,
        periodType = BudgetPeriod.MONTHLY,
    )

    private fun spend(amount: String, date: LocalDate) = Transaction(
        amount = BigDecimal(amount),
        comment = "",
        date = date.atStartOfDay(),
        createdAt = 0L,
    )

    @Test
    fun `null or non-positive target yields null progress`() {
        val result = SavingsGoalAggregator.compute(
            archives = emptyList(),
            settings = settings(BigDecimal("1000")),
            transactions = emptyList(),
            savingsPct = 20,
            target = null,
            today = today,
        )
        assertThat(result).isNull()

        val zero = SavingsGoalAggregator.compute(
            archives = emptyList(),
            settings = settings(BigDecimal("1000")),
            transactions = emptyList(),
            savingsPct = 20,
            target = BigDecimal.ZERO,
            today = today,
        )
        assertThat(zero).isNull()
    }

    @Test
    fun `archives and current period accumulate into saved total`() {
        // Aug: 1000 budget, 600 spent → 40% of 400 = 160 saved.
        // Sep (current): 1000 budget, 200 spent → 40% of 800 = 320 saved.
        val progress = SavingsGoalAggregator.compute(
            archives = listOf(archive(LocalDate.of(2026, 8, 1), BigDecimal("1000"), BigDecimal("600"))),
            settings = settings(BigDecimal("1000")),
            transactions = listOf(
                spend("120", LocalDate.of(2026, 9, 3)),
                spend("80", LocalDate.of(2026, 9, 10)),
            ),
            savingsPct = 40,
            target = BigDecimal("1000"),
            today = today,
        )

        assertThat(progress).isNotNull()
        assertThat(progress!!.saved).isEqualTo(BigDecimal("480.00"))
        assertThat(progress.target).isEqualTo(BigDecimal("1000"))
        assertThat(progress.progressFraction).isWithin(1e-6f).of(0.48f)
    }

    @Test
    fun `archives in other currencies are ignored`() {
        val progress = SavingsGoalAggregator.compute(
            archives = listOf(
                archive(LocalDate.of(2026, 8, 1), BigDecimal("1000"), BigDecimal("0"), currencyCode = "EUR"),
            ),
            settings = settings(BigDecimal("1000")),
            transactions = emptyList(),
            savingsPct = 40,
            target = BigDecimal("500"),
            today = today,
        )

        assertThat(progress).isNotNull()
        // Only the current period contributed: 40% of 1000 = 400.
        assertThat(progress!!.saved).isEqualTo(BigDecimal("400.00"))
    }

    @Test
    fun `negative savings month does not reduce cumulative saved`() {
        val progress = SavingsGoalAggregator.compute(
            archives = listOf(
                // Overspent: saved = 500-800 = -300 → clamped to 0 by the calculator.
                archive(LocalDate.of(2026, 7, 1), BigDecimal("500"), BigDecimal("800")),
                archive(LocalDate.of(2026, 8, 1), BigDecimal("1000"), BigDecimal("600")),
            ),
            settings = null,
            transactions = emptyList(),
            savingsPct = 40,
            target = BigDecimal("500"),
            today = today,
        )

        assertThat(progress).isNotNull()
        assertThat(progress!!.saved).isEqualTo(BigDecimal("160.00"))
    }

    @Test
    fun `adjustments and deleted transactions are excluded from period spend`() {
        val deleted = spend("500", LocalDate.of(2026, 9, 5)).copy(isDeleted = true)
        val adjusted = spend("300", LocalDate.of(2026, 9, 6)).copy(isAdjustment = true)

        val progress = SavingsGoalAggregator.compute(
            archives = emptyList(),
            settings = settings(BigDecimal("1000")),
            transactions = listOf(deleted, adjusted, spend("100", LocalDate.of(2026, 9, 7))),
            savingsPct = 40,
            target = BigDecimal("1000"),
            today = today,
        )

        assertThat(progress).isNotNull()
        // Only the 100 spend counts: 40% of 900 = 360.
        assertThat(progress!!.saved).isEqualTo(BigDecimal("360.00"))
    }
}
