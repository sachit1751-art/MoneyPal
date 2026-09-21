package com.sachit.moneypal.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class BudgetRepositorySumTest {

    @Test
    fun `empty list sums to zero`() {
        assertThat(sumAmounts(emptyList())).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `two amounts sum exactly`() {
        val sum = sumAmounts(listOf("10.50", "2.25"))

        assertThat(sum.compareTo(BigDecimal("12.75"))).isEqualTo(0)
    }

    @Test
    fun `float-hostile amounts sum exactly`() {
        val sum = sumAmounts(listOf("0.1", "0.2"))

        assertThat(sum.compareTo(BigDecimal("0.3"))).isEqualTo(0)
    }

    @Test
    fun `large amounts accumulate without drift`() {
        val sum = sumAmounts(listOf("999999999999.99", "999999999999.99", "0.01"))

        assertThat(sum.compareTo(BigDecimal("1999999999999.99"))).isEqualTo(0)
    }

    @Test
    fun `unparseable amount throws NumberFormatException`() {
        assertThrows(NumberFormatException::class.java) {
            sumAmounts(listOf("10.00", "abc"))
        }
    }
}
