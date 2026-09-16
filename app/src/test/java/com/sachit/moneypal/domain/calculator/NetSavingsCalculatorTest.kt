package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class NetSavingsCalculatorTest {

    private val calculator = NetSavingsCalculator()

    private fun tx(
        amount: String,
        isIncome: Boolean = false,
        isDeleted: Boolean = false,
        isAdjustment: Boolean = false,
    ) = Transaction(
        id = 0L,
        amount = BigDecimal(amount),
        comment = "",
        date = null,
        isIncome = isIncome,
        isDeleted = isDeleted,
        isAdjustment = isAdjustment,
    )

    @Test
    fun `empty list yields zeros`() {
        val result = calculator.compute(emptyList())
        assertEquals(BigDecimal.ZERO, result.income)
        assertEquals(BigDecimal.ZERO, result.spent)
        assertEquals(BigDecimal.ZERO, result.net)
    }

    @Test
    fun `income only`() {
        val result = calculator.compute(listOf(tx("100", isIncome = true), tx("50", isIncome = true)))
        assertEquals(BigDecimal("150"), result.income)
        assertEquals(BigDecimal.ZERO, result.spent)
        assertEquals(BigDecimal("150"), result.net)
    }

    @Test
    fun `expenses only`() {
        val result = calculator.compute(listOf(tx("30"), tx("20")))
        assertEquals(BigDecimal.ZERO, result.income)
        assertEquals(BigDecimal("50"), result.spent)
        assertEquals(BigDecimal("-50"), result.net)
    }

    @Test
    fun `mixed income and expenses`() {
        val result = calculator.compute(
            listOf(tx("100", isIncome = true), tx("40"), tx("10"))
        )
        assertEquals(BigDecimal("100"), result.income)
        assertEquals(BigDecimal("50"), result.spent)
        assertEquals(BigDecimal("50"), result.net)
    }

    @Test
    fun `deleted and adjustment rows are ignored`() {
        val result = calculator.compute(
            listOf(
                tx("100", isIncome = true),
                tx("999", isDeleted = true),
                tx("999", isAdjustment = true),
            )
        )
        assertEquals(BigDecimal("100"), result.income)
        assertEquals(BigDecimal.ZERO, result.spent)
        assertEquals(BigDecimal("100"), result.net)
    }

    @Test
    fun `negative net when spending exceeds income`() {
        val result = calculator.compute(
            listOf(tx("10", isIncome = true), tx("80"))
        )
        assertEquals(BigDecimal("-70"), result.net)
    }
}
