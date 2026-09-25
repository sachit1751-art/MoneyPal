package com.sachit.moneypal.presentation.ui.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.sachit.moneypal.R
import com.sachit.moneypal.presentation.util.font.format.formatCurrencySymbolOnly
import java.math.BigDecimal

/**
 * Compact "Cash on hand" card (plan 043): shows the wallet balance derived
 * from the starting balance minus cash spend. Hidden when the user never set
 * a starting balance. Long-press-free: the "edit" affordance opens a dialog
 * to set/clear the starting balance.
 */
@Composable
fun CashOnHandCard(
    balance: BigDecimal?,
    currency: String,
    onSetStartingBalance: (BigDecimal) -> Unit,
    onClearStartingBalance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    balance ?: return
    var showEditor by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        onClick = { showEditor = true },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.height(0.dp))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.cash_on_hand_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.cash_on_hand_edit_hint),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatCurrencySymbolOnly(balance, currency),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    if (showEditor) {
        CashStartingBalanceDialog(
            current = balance,
            currency = currency,
            onDismiss = { showEditor = false },
            onConfirm = { value ->
                showEditor = false
                if (value == null) {
                    onClearStartingBalance()
                } else {
                    onSetStartingBalance(value)
                }
            },
        )
    }
}

@Composable
private fun CashStartingBalanceDialog(
    current: BigDecimal,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal?) -> Unit,
) {
    var text by remember { mutableStateOf(current.toPlainString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cash_balance_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.cash_balance_dialog_message))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text(formatCurrencySymbolOnly(BigDecimal.ZERO, currency).trimEnd('0', '.', ' ', ',')) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    text.toBigDecimalOrNull()?.let { onConfirm(it) } ?: onDismiss()
                },
            ) { Text(stringResource(R.string.cash_balance_dialog_save)) }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(null) }) {
                Text(stringResource(R.string.cash_balance_dialog_clear))
            }
        },
    )
}
