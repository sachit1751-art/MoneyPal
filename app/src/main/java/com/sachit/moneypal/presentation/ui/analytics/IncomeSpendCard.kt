package com.sachit.moneypal.presentation.ui.analytics

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sachit.moneypal.R
import com.sachit.moneypal.domain.calculator.IncomeSpendComparison
import com.sachit.moneypal.presentation.ui.theme.colorBad
import com.sachit.moneypal.presentation.ui.theme.colorGood
import com.sachit.moneypal.presentation.util.font.format.formatCurrencySymbolOnly
import java.math.BigDecimal

/**
 * "Income vs spend" card (plan 046): two share bars for the period's
 * spend/income, a net badge, and delta arrows vs the previous period.
 * Collapsible; hidden entirely when the caller passes null (no income
 * feature data / both totals zero and no history).
 */
@Composable
fun IncomeSpendCard(
    comparison: IncomeSpendComparison,
    currency: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val money = remember(currency) { { v: BigDecimal -> formatCurrencySymbolOnly(v, currency) } }
    val netPositive = comparison.net.signum() >= 0

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            ) {
                Icon(
                    imageVector = Icons.Filled.SwapVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.padding(start = 10.dp))
                Text(
                    text = stringResource(R.string.income_spend_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = money(comparison.net),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (netPositive) colorGood else colorBad,
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                ShareBar(
                    label = stringResource(R.string.income_spend_card_spent),
                    amount = money(comparison.totalSpend),
                    fraction = shareFraction(comparison.totalSpend, comparison.totalIncome),
                    barColor = colorBad,
                )
                Spacer(Modifier.height(8.dp))
                ShareBar(
                    label = stringResource(R.string.income_spend_card_income),
                    amount = money(comparison.totalIncome),
                    fraction = shareFraction(comparison.totalIncome, comparison.totalSpend),
                    barColor = colorGood,
                )

                comparison.savingsRatePct?.let { rate ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.income_spend_card_savings_rate, rate.toPlainString()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                val incomeDelta = comparison.incomeDeltaPct
                val spendDelta = comparison.spendDeltaPct
                if (incomeDelta != null || spendDelta != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        incomeDelta?.let {
                            DeltaChip(stringResource(R.string.income_spend_card_income), it)
                        }
                        spendDelta?.let {
                            Spacer(Modifier.padding(start = 12.dp))
                            DeltaChip(stringResource(R.string.income_spend_card_spent), it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareBar(
    label: String,
    amount: String,
    fraction: Float,
    barColor: androidx.compose.ui.graphics.Color,
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text(amount, style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@Composable
private fun DeltaChip(label: String, deltaPct: BigDecimal) {
    val up = deltaPct.signum() >= 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 2.dp),
        )
        Text(
            text = stringResource(
                R.string.income_spend_card_delta,
                label,
                deltaPct.toPlainString(),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun shareFraction(part: BigDecimal, other: BigDecimal): Float {
    val total = part.add(other)
    if (total.signum() <= 0) return 0f
    return part.divide(total, 4, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
}
