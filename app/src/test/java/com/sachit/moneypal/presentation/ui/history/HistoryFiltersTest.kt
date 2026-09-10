package com.sachit.moneypal.presentation.ui.history

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.domain.model.Transaction
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class HistoryFiltersTest {

    private val categoryNames = mapOf(
        1L to "Food",
        2L to "Transport",
        3L to "Pharmacy",
    )

    private fun tx(
        id: Long,
        amount: String,
        comment: String = "",
        categoryId: Long? = null,
        isRecurrent: Boolean = false,
        isCredit: Boolean = false,
    ): Transaction = Transaction.create(
        amount = BigDecimal(amount),
        comment = comment,
        date = LocalDateTime.of(2026, 9, 1, 12, 0),
        categoryId = categoryId,
        isRecurrent = isRecurrent,
        isCredit = isCredit,
    ).copy(id = id)

    private val fixtures = listOf(
        tx(1, "450.00", comment = "Pharmacy run", categoryId = 3L),
        tx(2, "120.50", comment = "Lunch", categoryId = 1L),
        tx(3, "-2000.00", comment = "Salary", categoryId = null),
        tx(4, "60.00", comment = "Bus", categoryId = 2L, isRecurrent = true),
        tx(5, "900.00", comment = "Laptop", isCredit = true),
    )

    @Test
    fun `inactive filter returns the input list unchanged`() {
        val result = filterTransactions(fixtures, HistoryFilterState(), categoryNames)
        assertThat(result).isEqualTo(fixtures)
    }

    @Test
    fun `query matches comment case-insensitively`() {
        val filter = HistoryFilterState(query = "pharmacy")
        val result = filterTransactions(fixtures, filter, categoryNames)
        assertThat(result.map { it.id }).containsExactly(1L)
    }

    @Test
    fun `query matches category name`() {
        val filter = HistoryFilterState(query = "transport")
        val result = filterTransactions(fixtures, filter, categoryNames)
        assertThat(result.map { it.id }).containsExactly(4L)
    }

    @Test
    fun `query matches plain amount string`() {
        val filter = HistoryFilterState(query = "450")
        val result = filterTransactions(fixtures, filter, categoryNames)
        assertThat(result.map { it.id }).containsExactly(1L)
    }

    @Test
    fun `category filter is exact-match by name`() {
        val filter = HistoryFilterState(categoryName = "Food")
        val result = filterTransactions(fixtures, filter, categoryNames)
        assertThat(result.map { it.id }).containsExactly(2L)
    }

    @Test
    fun `category filter excludes uncategorized transactions`() {
        val filter = HistoryFilterState(categoryName = "Food")
        val result = filterTransactions(fixtures, filter, categoryNames)
        assertThat(result.map { it.id }).doesNotContain(3L)
    }

    @Test
    fun `min amount compares against absolute value so income is searchable`() {
        val filter = HistoryFilterState(minAmount = BigDecimal("1000"))
        val result = filterTransactions(fixtures, filter, categoryNames)
        // Salary is -2000 (abs 2000) and Laptop is 900 → only salary passes.
        assertThat(result.map { it.id }).containsExactly(3L)
    }

    @Test
    fun `amount bounds are inclusive`() {
        val filter = HistoryFilterState(
            minAmount = BigDecimal("60.00"),
            maxAmount = BigDecimal("450.00"),
        )
        val result = filterTransactions(fixtures, filter, categoryNames)
        // 450.00 and 60.00 hit the bounds; 120.50 is inside; salary (abs 2000)
        // and laptop (900) are outside.
        assertThat(result.map { it.id }).containsExactly(1L, 2L, 4L)
    }

    @Test
    fun `recurrent only keeps recurrent transactions`() {
        val filter = HistoryFilterState(recurrentOnly = true)
        val result = filterTransactions(fixtures, filter, categoryNames)
        assertThat(result.map { it.id }).containsExactly(4L)
    }

    @Test
    fun `credit only keeps credit transactions`() {
        val filter = HistoryFilterState(creditOnly = true)
        val result = filterTransactions(fixtures, filter, categoryNames)
        assertThat(result.map { it.id }).containsExactly(5L)
    }

    @Test
    fun `combined predicates compose`() {
        val filter = HistoryFilterState(
            query = "run",
            categoryName = "Pharmacy",
            minAmount = BigDecimal("100"),
        )
        val result = filterTransactions(fixtures, filter, categoryNames)
        assertThat(result.map { it.id }).containsExactly(1L)
    }

    @Test
    fun `combined predicates reject non-matching combinations`() {
        val filter = HistoryFilterState(
            query = "lunch",
            categoryName = "Pharmacy",
        )
        val result = filterTransactions(fixtures, filter, categoryNames)
        assertThat(result).isEmpty()
    }
}
