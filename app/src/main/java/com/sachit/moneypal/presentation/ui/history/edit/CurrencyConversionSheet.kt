package com.sachit.moneypal.presentation.ui.history.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sachit.moneypal.R
import com.sachit.moneypal.domain.calculator.CurrencyConversionCalculator
import com.sachit.moneypal.domain.model.SupportedCurrency
import com.sachit.moneypal.domain.model.SupportedCurrencyData
import java.math.BigDecimal

/**
 * Bottom sheet for entering an expense in a foreign currency. The user picks
 * the currency, types the foreign amount and the exchange rate; the converted
 * budget-currency amount is previewed live and returned together with the
 * original-currency snapshot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyConversionSheet(
    budgetCurrencyCode: String,
    initialForeignCurrency: String? = null,
    initialForeignAmount: BigDecimal? = null,
    calculator: CurrencyConversionCalculator,
    onApply: (
        foreignCurrency: String,
        foreignAmount: BigDecimal,
        rate: BigDecimal,
        convertedAmount: BigDecimal,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val popularCurrencies = remember {
        listOf("USD", "EUR", "GBP", "JPY", "INR", "AED", "CAD", "AUD", "CHF", "SGD")
    }
    var selectedCurrency by remember {
        mutableStateOf(
            initialForeignCurrency
                ?: SupportedCurrency.ALL.firstOrNull { it.code != budgetCurrencyCode }?.code
                ?: "USD"
        )
    }
    var amountText by remember {
        mutableStateOf(initialForeignAmount?.toPlainString() ?: "")
    }
    var rateText by remember { mutableStateOf("") }

    val foreignAmount = amountText.toBigDecimalOrNull()
    val rate = rateText.toBigDecimalOrNull()
    val converted = if (foreignAmount != null && rate != null) {
        calculator.convert(foreignAmount, rate)
    } else {
        null
    }

    val foreignSymbol = SupportedCurrency.findByCode(selectedCurrency)?.symbol ?: selectedCurrency
    val budgetSymbol =
        SupportedCurrencyData.findByCode(budgetCurrencyCode)?.symbol ?: budgetCurrencyCode

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.currency_convert_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = stringResource(R.string.currency_convert_pick),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Popular currencies first for fast access while travelling.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                popularCurrencies.take(5).forEach { code ->
                    FilterChip(
                        selected = selectedCurrency == code,
                        onClick = { selectedCurrency = code },
                        label = { Text(code) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                popularCurrencies.drop(5).forEach { code ->
                    FilterChip(
                        selected = selectedCurrency == code,
                        onClick = { selectedCurrency = code },
                        label = { Text(code) },
                    )
                }
                FilterChip(
                    selected = selectedCurrency !in popularCurrencies,
                    onClick = onDismiss,
                    label = {
                        Text(stringResource(R.string.currency_convert_more))
                    },
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = {
                    Text(stringResource(R.string.currency_convert_amount_in, foreignSymbol))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = rateText,
                onValueChange = { rateText = it },
                label = {
                    Text(
                        stringResource(
                            R.string.currency_convert_rate_hint, foreignSymbol, budgetSymbol
                        )
                    )
                },
                supportingText = {
                    Text(stringResource(R.string.currency_convert_rate_example, budgetSymbol))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (converted != null) {
                Text(
                    text = stringResource(
                        R.string.currency_convert_preview,
                        budgetSymbol + converted.toPlainString(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.height(0.dp))
                TextButton(
                    onClick = {
                        val fa = foreignAmount
                        val r = rate
                        val c = converted
                        if (fa != null && r != null && c != null) {
                            onApply(selectedCurrency, fa, r, c)
                        }
                    },
                    enabled = converted != null,
                ) {
                    Text(stringResource(R.string.accept))
                }
            }
        }
    }
}
