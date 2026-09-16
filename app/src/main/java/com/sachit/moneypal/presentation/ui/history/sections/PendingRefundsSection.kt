package com.sachit.moneypal.presentation.ui.history.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import java.time.temporal.ChronoUnit

/**
 * Pending refunds card (plan 017): expenses flagged `refundExpected` that
 * have not been settled yet, each with a "Received" action that credits the
 * budget. Rendered only when the list is non-empty.
 */
@Composable
internal fun PendingRefundsSection(
    pendingRefunds: List<Transaction>,
    onReceived: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pendingRefunds.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.refund_section_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        pendingRefunds.forEach { transaction ->
            val daysPending = transaction.date?.toLocalDate()?.let { date ->
                ChronoUnit.DAYS.between(date, java.time.LocalDate.now()).coerceAtLeast(0)
            } ?: 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.comment.ifEmpty {
                            stringResource(R.string.generic_expense)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.refund_pending_days, daysPending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatCurrency(transaction.amount),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = { onReceived(transaction) }) {
                    Text(stringResource(R.string.refund_received))
                }
            }
        }
    }
}
