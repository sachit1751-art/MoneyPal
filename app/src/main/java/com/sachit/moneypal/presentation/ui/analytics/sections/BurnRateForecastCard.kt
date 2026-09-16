package com.sachit.moneypal.presentation.ui.analytics.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.sachit.moneypal.R
import com.sachit.moneypal.presentation.ui.analytics.util.BurnRateForecastUiModel
import com.sachit.moneypal.presentation.util.font.format.symbolOnlyCurrencyFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Burn-rate forecast card (plan 018): surfaces the pace math
 * [com.sachit.moneypal.domain.calculator.BurnRateCalculator] already computes.
 * On pace → neutral; over pace → warning colors with the projected overshoot
 * amount and exhaustion date. Text-first; tapping goes to the main screen
 * where budget adjustments live.
 */
@Composable
fun BurnRateForecastCard(
    forecast: BurnRateForecastUiModel,
    currency: String,
    exhaustionDate: java.time.LocalDate?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currencyFormat = symbolOnlyCurrencyFormat(currency)
    val container = if (forecast.onPace) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val content = if (forecast.onPace) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (forecast.onPace) "✅" else "⚠️",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.forecast_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                val detail = if (forecast.onPace) {
                    stringResource(R.string.forecast_on_pace_line, forecast.pacePercent)
                } else {
                    val dateText = exhaustionDate?.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    ) ?: stringResource(R.string.forecast_period_end)
                    stringResource(
                        R.string.forecast_over_pace_line,
                        currencyFormat.format(forecast.projectedOverspend),
                        dateText,
                    )
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = content.copy(alpha = 0.8f),
                )
                Text(
                    text = stringResource(R.string.forecast_at_this_pace),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.6f),
                )
            }
        }
    }
}
