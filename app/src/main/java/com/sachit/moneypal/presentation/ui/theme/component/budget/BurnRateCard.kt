package com.sachit.moneypal.presentation.ui.theme.component.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sachit.moneypal.R
import com.sachit.moneypal.domain.model.BudgetState
import com.sachit.moneypal.presentation.ui.theme.MinusTheme
import com.sachit.moneypal.presentation.util.combineColors
import com.sachit.moneypal.presentation.util.font.format.numberFormat
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Analytics card showing the spending-pace projection derived from
 * [BudgetState.pacePercent] / [BudgetState.projectedExhaustionDate].
 *
 * Copy variants:
 * - pace > 105 → "spending X% faster than budgeted; on pace to run out on <date>"
 * - pace < 95  → "spending X% slower than budgeted"
 * - otherwise  → "right on pace"
 */
@Composable
fun BurnRateCard(
    budgetState: BudgetState,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formatOverspend = remember(budgetState.projectedOverspend, currency) {
        numberFormat(context, budgetState.projectedOverspend, currency = currency)
    }

    val pacePercent = budgetState.pacePercent
    val exhaustionDate = budgetState.projectedExhaustionDate

    val headline: String
    val detail: String
    if (budgetState.totalSpentInPeriod <= BigDecimal.ZERO) {
        headline = stringResource(R.string.burn_rate_title)
        detail = stringResource(R.string.burn_rate_no_spending)
    } else if (pacePercent > 105) {
        headline = stringResource(R.string.burn_rate_over_pace_title, pacePercent - 100)
        detail = if (exhaustionDate != null) {
            stringResource(
                R.string.burn_rate_over_pace_detail_with_date,
                formatOverspend,
                exhaustionDate.toString(),
            )
        } else {
            stringResource(R.string.burn_rate_over_pace_detail, formatOverspend)
        }
    } else if (pacePercent < 95) {
        headline = stringResource(R.string.burn_rate_under_pace_title, 100 - pacePercent)
        detail = stringResource(R.string.burn_rate_under_pace_detail)
    } else {
        headline = stringResource(R.string.burn_rate_on_pace_title)
        detail = stringResource(R.string.burn_rate_on_pace_detail)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = combineColors(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceVariant,
                t = 0.3f,
            ),
        ),
    ) {
        Column(modifier = Modifier.padding(PaddingValues(vertical = 12.dp, horizontal = 16.dp))) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = if (pacePercent > 105) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewBurnRateCard() {
    MinusTheme {
        BurnRateCard(
            budgetState = BudgetState(
                remainingToday = BigDecimal.ZERO,
                totalSpentToday = BigDecimal.ZERO,
                dailyBudget = BigDecimal.TEN,
                daysRemaining = 10,
                progress = 0.5f,
                isOverBudget = false,
                totalBudget = BigDecimal(3000),
                totalSpentInPeriod = BigDecimal(2000),
                pacePercent = 130,
                projectedOverspend = BigDecimal("600.00"),
                projectedExhaustionDate = LocalDate.now().plusDays(8),
            ),
            currency = "USD",
        )
    }
}
