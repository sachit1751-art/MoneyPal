@file:OptIn(ExperimentalMaterial3Api::class)

package com.sachit.moneypal.presentation.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import java.math.BigDecimal

/**
 * Amount-range filter sheet for the History screen (plan 008). Local text state
 * only — nothing is dispatched until Apply is pressed, then a single
 * [HistoryFilterIntent.SetAmountFilter] intent is emitted.
 *
 * Bounds are inclusive (see [filterTransactions]) and compare against the
 * absolute amount, so income entries are searchable too.
 */
@Composable
fun HistoryAmountFilterSheet(
    currentFilter: HistoryFilterState,
    onProcessIntent: (HistoryUiIntent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Empty string means "no bound"; parse errors keep Apply disabled.
    var minText by remember(currentFilter.minAmount) {
        mutableStateOf(currentFilter.minAmount?.toPlainString().orEmpty())
    }
    var maxText by remember(currentFilter.maxAmount) {
        mutableStateOf(currentFilter.maxAmount?.toPlainString().orEmpty())
    }

    val minAmount = minText.toAmountOrNull()
    val maxAmount = maxText.toAmountOrNull()
    val minValid = minText.isBlank() || minAmount != null
    val maxValid = maxText.isBlank() || maxAmount != null
    val canApply = minValid && maxValid &&
        (minAmount != null || maxAmount != null) &&
        (minAmount == null || maxAmount == null || minAmount <= maxAmount)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.history_filter_amount_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.history_filter_amount_min)) },
                    isError = !minValid,
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.history_filter_amount_max)) },
                    isError = !maxValid,
                )
            }

            if (!minValid || !maxValid) {
                Text(
                    text = stringResource(R.string.history_filter_amount_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onProcessIntent(HistoryFilterIntent.SetAmountFilter(null, null))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.history_filter_clear))
                }
                Button(
                    onClick = {
                        onProcessIntent(HistoryFilterIntent.SetAmountFilter(minAmount, maxAmount))
                        onDismiss()
                    },
                    enabled = canApply,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.history_filter_apply))
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    }
}

/** Parses a user-typed amount; blank means "unbounded", garbage means null. */
private fun String.toAmountOrNull(): BigDecimal? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        BigDecimal(trimmed.replace(',', '.'))
    }.getOrNull()
}
