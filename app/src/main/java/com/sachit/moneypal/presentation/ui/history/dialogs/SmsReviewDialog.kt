package com.sachit.moneypal.presentation.ui.history.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sachit.moneypal.R
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.presentation.util.font.format.formatCurrency

/**
 * Review inbox for low-confidence SMS captures (plan 015). Confirm trusts the
 * row (confidence = 100), Edit opens the standard transaction editor, Delete
 * removes it. Rows leave the list reactively as they are resolved.
 */
@Composable
fun SmsReviewDialog(
    candidates: List<Transaction>,
    currencyCode: String,
    onConfirm: (Transaction) -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onMuteSender: (Transaction) -> Unit = {},
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sms_review_title)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.sms_review_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(candidates, key = { it.id }) { transaction ->
                        SmsReviewRow(
                            transaction = transaction,
                            currencyCode = currencyCode,
                            onConfirm = onConfirm,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onMuteSender = onMuteSender,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sms_review_close))
            }
        },
    )
}

@Composable
private fun SmsReviewRow(
    transaction: Transaction,
    currencyCode: String,
    onConfirm: (Transaction) -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onMuteSender: (Transaction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.comment.ifEmpty {
                        stringResource(R.string.generic_expense)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(
                        R.string.sms_review_confidence,
                        transaction.captureConfidence ?: 0,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatCurrency(transaction.amount, currencyCode),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(modifier = Modifier.align(Alignment.End)) {
            if (!transaction.smsSender.isNullOrBlank()) {
                TextButton(onClick = { onMuteSender(transaction) }) {
                    Text(stringResource(R.string.sms_review_mute_sender))
                }
            }
            TextButton(onClick = { onDelete(transaction) }) {
                Text(stringResource(R.string.sms_review_delete))
            }
            TextButton(onClick = { onEdit(transaction) }) {
                Text(stringResource(R.string.sms_review_edit))
            }
            TextButton(onClick = { onConfirm(transaction) }) {
                Text(stringResource(R.string.sms_review_confirm))
            }
        }
    }
}
