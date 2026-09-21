package com.sachit.moneypal.presentation.ui.budget

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.data.repository.matchesStoredAmount
import org.junit.Test
import java.math.BigDecimal

class DuplicateCheckMatchingTest {

    @Test
    fun `plain amount matches itself`() {
        assertThat(BigDecimal("50").let { "50".matchesStoredAmount(it) }).isTrue()
    }

    @Test
    fun `scale differences match numerically`() {
        assertThat(BigDecimal("50").let { "50.0".matchesStoredAmount(it) }).isTrue()
        assertThat(BigDecimal("50").let { "50.00".matchesStoredAmount(it) }).isTrue()
    }

    @Test
    fun `scientific notation matches numerically`() {
        assertThat(BigDecimal("50").let { "5E+1".matchesStoredAmount(it) }).isTrue()
    }

    @Test
    fun `different amounts never match`() {
        assertThat(BigDecimal("0.1").let { "0.11".matchesStoredAmount(it) }).isFalse()
        assertThat(BigDecimal("50").let { "49.99".matchesStoredAmount(it) }).isFalse()
    }

    @Test
    fun `corrupted amount row is skipped without throwing`() {
        assertThat(BigDecimal("50").let { "abc".matchesStoredAmount(it) }).isFalse()
    }

    @Test
    fun `null or blank stored amount never matches`() {
        val nullAmount: String? = null
        assertThat(BigDecimal("50").let { nullAmount.matchesStoredAmount(it) }).isFalse()
        assertThat(BigDecimal("50").let { "  ".matchesStoredAmount(it) }).isFalse()
    }
}
