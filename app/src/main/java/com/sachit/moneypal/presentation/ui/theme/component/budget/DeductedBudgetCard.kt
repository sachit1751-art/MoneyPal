package com.sachit.moneypal.presentation.ui.theme.component.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sachit.moneypal.R
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.presentation.ui.theme.MinusTheme
import com.sachit.moneypal.presentation.ui.theme.SlantedShape
import com.sachit.moneypal.presentation.ui.theme.labelMediumCondensed
import com.sachit.moneypal.presentation.util.censor
import com.sachit.moneypal.presentation.util.combineColors
import com.sachit.moneypal.presentation.util.font.format.numberFormat
import java.math.BigDecimal

@Composable
fun DeductedBudgetCard(
    modifier: Modifier = Modifier,
    decreases: List<Transaction>,
    currency: String = "USD",
) {
    val context = LocalContext.current
    val totalDeducted = remember(decreases) {
        decreases.sumOf { it.amount }
    }

    Card(
        modifier = modifier,
        shape = SlantedShape,
        colors = CardDefaults.cardColors(
            containerColor = combineColors(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.errorContainer,
                t = 0.5f,
            ),
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = numberFormat(context, totalDeducted, currency = currency),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.censor(),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.deducted_budget),
                style = MaterialTheme.typography.labelMediumCondensed,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(device = "spec:width=500px,height=500px,dpi=440")
@Composable
private fun PreviewDeductedBudgetCard() {
    MinusTheme {
        DeductedBudgetCard(
            decreases = listOf(
                Transaction(amount = BigDecimal("100"), categoryId = null, date = null),
                Transaction(amount = BigDecimal("200"), categoryId = null, date = null),
            )
        )
    }
}
