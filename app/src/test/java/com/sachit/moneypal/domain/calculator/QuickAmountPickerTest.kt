package com.sachit.moneypal.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.domain.model.Transaction
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class QuickAmountPickerTest {

    private val picker = QuickAmountPicker()

    private fun tx(
        id: Long,
        amount: String,
        createdAt: Long = 0L,
        isCredit: Boolean = false,
        isAdjustment: Boolean = false,
        isRecurrent: Boolean = false,
    ) = Transaction(
        id = id,
        amount = BigDecimal(amount),
        comment = "x",
        date = LocalDateTime.of(2026, 9, 1, 12, 0),
        createdAt = createdAt,
        isCredit = isCredit,
        isAdjustment = isAdjustment,
        isRecurrent = isRecurrent,
    )

    @Test
    fun `empty history yields empty list`() {
        assertThat(picker.pick(emptyList())).isEmpty()
    }

    @Test
    fun `frequent amount ranks first and qualifies at minUses`() {
        val history = listOf(
            tx(1, "50"), tx(2, "50"), tx(3, "50"),
            tx(4, "20"), tx(5, "20"),
        )
        assertThat(picker.pick(history)).containsExactly(BigDecimal("50"))
    }

    @Test
    fun `ties broken by recency`() {
        val history = listOf(
            tx(1, "50", createdAt = 100L), tx(2, "50", createdAt = 300L), tx(3, "50", createdAt = 100L),
            tx(4, "20", createdAt = 100L), tx(5, "20", createdAt = 100L), tx(6, "20", createdAt = 500L),
        )
        // Both used 3 times; 20 was used most recently.
        assertThat(picker.pick(history)).containsExactly(BigDecimal("20"), BigDecimal("50")).inOrder()
    }

    @Test
    fun `credits adjustments and recurrings are excluded`() {
        val history = listOf(
            tx(1, "50"), tx(2, "50"), tx(3, "50"),
            tx(4, "70", isCredit = true), tx(5, "70", isCredit = true), tx(6, "70", isCredit = true),
            tx(7, "80", isAdjustment = true), tx(8, "80", isAdjustment = true), tx(9, "80", isAdjustment = true),
            tx(10, "90", isRecurrent = true), tx(11, "90", isRecurrent = true), tx(12, "90", isRecurrent = true),
        )
        assertThat(picker.pick(history)).containsExactly(BigDecimal("50"))
    }

    @Test
    fun `trailing zeros merge - 50_00 equals 50`() {
        val history = listOf(
            tx(1, "50.00"), tx(2, "50"), tx(3, "50.0"),
        )
        assertThat(picker.pick(history)).containsExactly(BigDecimal("50"))
    }

    @Test
    fun `cap at max amounts`() {
        val history = (1..5).flatMap { n ->
            val amount = (n * 10).toString()
            listOf(tx(n * 3L, amount), tx(n * 3L + 1, amount), tx(n * 3L + 2, amount))
        }
        assertThat(picker.pick(history)).hasSize(QuickAmountPicker.MAX_AMOUNTS)
    }

    @Test
    fun `minUses cutoff respected`() {
        val history = listOf(tx(1, "50"), tx(2, "50"))
        assertThat(picker.pick(history, minUses = 3)).isEmpty()
        assertThat(picker.pick(history, minUses = 2)).containsExactly(BigDecimal("50"))
    }
}
