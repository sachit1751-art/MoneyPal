package com.sachit.moneypal.domain.calculator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MaskedAmountFormatterTest {

    @Test
    fun `hide off returns the input unchanged`() {
        assertThat(MaskedAmountFormatter.format("$1,234.50", hide = false)).isEqualTo("$1,234.50")
    }

    @Test
    fun `hide on with symbol prefixes the mask`() {
        assertThat(MaskedAmountFormatter.format("$1,234.50", hide = true, currencySymbol = "$"))
            .isEqualTo("$•••")
    }

    @Test
    fun `hide on without symbol returns bare mask`() {
        assertThat(MaskedAmountFormatter.format("1,234.50", hide = true)).isEqualTo("•••")
    }

    @Test
    fun `blank symbol falls back to bare mask`() {
        assertThat(MaskedAmountFormatter.format("42", hide = true, currencySymbol = " "))
            .isEqualTo("•••")
    }

    @Test
    fun `hide on masks any input including empty`() {
        assertThat(MaskedAmountFormatter.format("", hide = true, currencySymbol = "€"))
            .isEqualTo("€•••")
    }
}
