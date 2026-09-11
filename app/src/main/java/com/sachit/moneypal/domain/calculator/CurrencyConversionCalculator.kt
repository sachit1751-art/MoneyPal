package com.sachit.moneypal.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Converts an amount entered in a foreign currency into the budget currency
 * using a user-provided exchange rate (no network — the FOSS flavor stays
 * offline-friendly).
 *
 * unit-tested in `CurrencyConversionCalculatorTest`.
 */
class CurrencyConversionCalculator @Inject constructor() {

    /**
     * @param amount Amount as entered, in the foreign currency.
     * @param rate How many budget-currency units one unit of the foreign
     *   currency is worth (e.g. 1 EUR = 0.92 USD budget → rate = 0.92).
     * @return The equivalent amount in the budget currency, rounded to 2 dp.
     */
    fun convert(amount: BigDecimal, rate: BigDecimal): BigDecimal {
        if (rate <= BigDecimal.ZERO) return amount
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
    }

    /** Formats the human-readable rate hint shown under the input. */
    fun rateHint(rate: BigDecimal): String {
        if (rate <= BigDecimal.ZERO) return ""
        return rate.stripTrailingZeros().toPlainString()
    }
}
