package com.sachit.moneypal.presentation.widget

import com.sachit.moneypal.presentation.util.font.format.formatCurrencySymbolOnly
import java.math.BigDecimal

fun formatWidgetCurrency(currency: String, amount: Int): String {
    return formatCurrencySymbolOnly(
        value = BigDecimal(amount),
        currencyCode = currency,
        minimumFractionDigits = 0,
    )
}
