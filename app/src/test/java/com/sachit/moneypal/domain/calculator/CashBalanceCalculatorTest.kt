package com.sachit.moneypal.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.domain.model.PaymentMethod
import com.sachit.moneypal.domain.model.Transaction
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Plan 043: cash balance math — spend subtracts, income adds, CASH-only
 * filtering, adjustment replaces the running total.
 */
class CashBalanceCalculatorTest {

    private val calculator = CashBalanceCalculator()

    private fun tx(
        id: Long,
        amount: String,
        day: Int = 1,
        hour: Int = 12,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        isIncome: Boolean = false,
        isAdjustment: Boolean = false,
        isDeleted: Boolean = false,
    ) = Transaction(
        id = id,
        amount = BigDecimal(amount),
        comment = "",
        date = LocalDateTime.of(2026, 9, day, hour, 0),
        paymentMethod = paymentMethod,
        isIncome = isIncome,
        isAdjustment = isAdjustment,
        isDeleted = isDeleted,
    )

    @Test
    fun `null starting balance yields null - feature not configured`() {
        val balance = calculator.currentBalance(
            starting = null,
            cashTransactions = listOf(tx(1, "10.00")),
        )
        assertThat(balance).isNull()
    }

    @Test
    fun `empty ledger returns the starting balance unchanged`() {
        val balance = calculator.currentBalance(
            starting = BigDecimal("100.00"),
            cashTransactions = emptyList(),
        )
        assertThat(balance).isEqualTo(BigDecimal("100.00"))
    }

    @Test
    fun `cash spend subtracts and cash income adds`() {
        val balance = calculator.currentBalance(
            starting = BigDecimal("100.00"),
            cashTransactions = listOf(
                tx(1, "30.00", day = 2),
                tx(2, "20.00", day = 3, isIncome = true),
                tx(3, "5.50", day = 4),
            ),
        )
        assertThat(balance).isEqualTo(BigDecimal("84.50"))
    }

    @Test
    fun `non-cash rows are ignored`() {
        val balance = calculator.currentBalance(
            starting = BigDecimal("100.00"),
            cashTransactions = listOf(
                tx(1, "40.00", paymentMethod = PaymentMethod.CARD),
                tx(2, "25.00", paymentMethod = PaymentMethod.OTHER),
            ),
        )
        assertThat(balance).isEqualTo(BigDecimal("100.00"))
    }

    @Test
    fun `deleted rows are ignored`() {
        val balance = calculator.currentBalance(
            starting = BigDecimal("100.00"),
            cashTransactions = listOf(tx(1, "40.00", isDeleted = true)),
        )
        assertThat(balance).isEqualTo(BigDecimal("100.00"))
    }

    @Test
    fun `adjustment replaces the running total with its amount`() {
        val balance = calculator.currentBalance(
            starting = BigDecimal("100.00"),
            cashTransactions = listOf(
                tx(1, "60.00", day = 2),
                tx(2, "30.00", day = 3, isAdjustment = true),
                tx(3, "10.00", day = 4),
            ),
        )
        // 100 - 60 = 40; adjustment sets 30; 30 - 10 = 20.
        assertThat(balance!!.compareTo(BigDecimal("20"))).isEqualTo(0)
    }

    @Test
    fun `result is independent of input order for add-subtract ledgers`() {
        val a = calculator.currentBalance(
            starting = BigDecimal("100.00"),
            cashTransactions = listOf(
                tx(1, "30.00", day = 2),
                tx(2, "10.00", day = 3),
            ),
        )
        val b = calculator.currentBalance(
            starting = BigDecimal("100.00"),
            cashTransactions = listOf(
                tx(2, "10.00", day = 3),
                tx(1, "30.00", day = 2),
            ),
        )
        assertThat(a).isEqualTo(b)
    }
}
