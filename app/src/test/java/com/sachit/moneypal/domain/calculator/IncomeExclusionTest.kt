package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.Category
import com.sachit.moneypal.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Regression guards for plan 006: income rows must never contribute to spend
 * totals anywhere in the budget math.
 */
class IncomeExclusionTest {

    private fun tx(
        amount: String,
        isIncome: Boolean = false,
        categoryId: Long? = null,
        day: LocalDate = LocalDate.of(2026, 9, 10),
    ) = Transaction(
        id = 0L,
        amount = BigDecimal(amount),
        comment = "",
        date = day.atStartOfDay(),
        categoryId = categoryId,
        isIncome = isIncome,
    )

    @Test
    fun `envelope progress ignores income rows even when category matches`() {
        val category = Category(id = 1L, name = "Food", monthlyLimit = BigDecimal("100"))
        val progress = EnvelopeCalculator().compute(
            transactions = listOf(
                tx("30", categoryId = 1L),
                tx("500", isIncome = true, categoryId = 1L),
            ),
            categories = listOf(category),
        )
        assertEquals(1, progress.size)
        assertEquals(BigDecimal("30"), progress[0].spent)
    }

    @Test
    fun `budget calculator spend totals exclude income`() {
        val settings = com.sachit.moneypal.domain.model.BudgetSettings(
            totalBudget = BigDecimal("1000"),
            period = com.sachit.moneypal.domain.model.BudgetPeriod.MONTHLY,
            startDate = LocalDate.of(2026, 9, 1),
        )
        val state = BudgetCalculator().calculate(
            settings = settings,
            transactions = listOf(
                tx("100"),
                tx("700", isIncome = true),
            ),
            currentDate = LocalDate.of(2026, 9, 10),
        )
        assertEquals(BigDecimal("100"), state.totalSpentInPeriod)
    }

    @Test
    fun `net savings counts income while budget math ignores it`() {
        val net = NetSavingsCalculator().compute(
            listOf(tx("700", isIncome = true), tx("100"))
        )
        assertEquals(BigDecimal("600"), net.net)
    }
}
