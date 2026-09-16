@file:OptIn(ExperimentalGlancePreviewApi::class)

package com.sachit.moneypal.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.FixedColorProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.sachit.moneypal.R
import com.sachit.moneypal.domain.calculator.SavingsGoalProgress
import com.sachit.moneypal.presentation.util.font.format.formatCurrencySymbolOnly
import java.time.format.DateTimeFormatter

private val savingsGoalSavedKey = stringPreferencesKey("savings_goal_saved")
private val savingsGoalTargetKey = stringPreferencesKey("savings_goal_target")
private val savingsGoalPercentKey = stringPreferencesKey("savings_goal_percent")
private val savingsGoalEtaKey = stringPreferencesKey("savings_goal_eta")
private val savingsGoalHasGoalKey = intPreferencesKey("savings_goal_has_goal")

class SavingsGoalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SavingsGoalWidget()
}

class SavingsGoalWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val prefs = currentState<Preferences>()

        SavingsGoalContent(
            hasGoal = (prefs[savingsGoalHasGoalKey] ?: 0) == 1,
            savedDisplay = prefs[savingsGoalSavedKey] ?: "",
            targetDisplay = prefs[savingsGoalTargetKey] ?: "",
            percentDisplay = prefs[savingsGoalPercentKey] ?: "",
            etaDisplay = prefs[savingsGoalEtaKey] ?: "",
            label = context.getString(R.string.widget_savings_goal_title),
            emptyLabel = context.getString(R.string.widget_savings_goal_empty),
        )
    }

    @Composable
    internal fun SavingsGoalContent(
        hasGoal: Boolean,
        savedDisplay: String,
        targetDisplay: String,
        percentDisplay: String,
        etaDisplay: String,
        label: String,
        emptyLabel: String,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .clickable(actionRunCallback<OpenAppAction>())
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(16.dp)
                    .background(Color(0x263F7DD5)),
                contentAlignment = Alignment.Center,
            ) {
                if (!hasGoal) {
                    Text(
                        text = emptyLabel,
                        style = TextStyle(
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = GlanceTheme.colors.onSurface,
                        ),
                        modifier = GlanceModifier.padding(horizontal = 12.dp),
                    )
                } else {
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = label,
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = GlanceTheme.colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                        )

                        Spacer(modifier = GlanceModifier.height(4.dp))

                        Text(
                            text = savedDisplay,
                            style = TextStyle(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.onSurface,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                        )

                        Text(
                            text = targetDisplay,
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = GlanceTheme.colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                        )

                        Spacer(modifier = GlanceModifier.height(8.dp))

                        SavingsGoalProgressBar(
                            fraction = percentDisplay.toProgressFractionOrNull() ?: 0f,
                        )

                        Spacer(modifier = GlanceModifier.height(6.dp))

                        Text(
                            text = listOf(percentDisplay, etaDisplay)
                                .filter { it.isNotEmpty() }
                                .joinToString(" · "),
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = GlanceTheme.colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Linear progress bar (plan 022): fractional fill inside a full-width track. */
@Composable
private fun SavingsGoalProgressBar(fraction: Float) {
    LinearProgressIndicator(
        progress = fraction.coerceIn(0f, 1f),
        modifier = GlanceModifier.fillMaxWidth().height(6.dp),
        color = FixedColorProvider(Color(0xFF3F7DD5)),
        backgroundColor = FixedColorProvider(Color(0x143F7DD5)),
    )
}

/** Reverse-maps the precomputed percent string (e.g. "45%") to a bar fraction. */
private fun String.toProgressFractionOrNull(): Float? {
    val percent = this.trimEnd('%').toFloatOrNull() ?: return null
    return (percent / 100f).coerceIn(0f, 1f)
}

/**
 * Pushes the latest goal snapshot into every placed savings-goal widget
 * (plan 022). A null [progress] renders the "no goal" empty state.
 */
suspend fun updateSavingsGoalWidget(
    context: Context,
    progress: SavingsGoalProgress?,
    currency: String,
) {
    val manager = GlanceAppWidgetManager(context)
    val glanceIds = manager.getGlanceIds(SavingsGoalWidget::class.java)
    if (glanceIds.isEmpty()) return

    fun money(value: java.math.BigDecimal): String = formatCurrencySymbolOnly(
        value = value,
        currencyCode = currency,
        maximumFractionDigits = 0,
        minimumFractionDigits = 0,
    )

    val percent = progress?.let { (it.progressFraction * 100).toInt().coerceIn(0, 100) }
    val percentDisplay = percent?.let { context.getString(R.string.widget_savings_goal_percent, it) } ?: ""
    val etaDisplay = progress?.estimatedFinishDate?.let {
        context.getString(
            R.string.widget_savings_goal_eta,
            it.format(DateTimeFormatter.ofPattern("MMM yyyy")),
        )
    } ?: ""

    glanceIds.forEach { glanceId ->
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[savingsGoalHasGoalKey] = if (progress != null) 1 else 0
            prefs[savingsGoalSavedKey] = progress?.let { money(it.saved) } ?: ""
            prefs[savingsGoalTargetKey] = progress?.let {
                context.getString(R.string.widget_savings_goal_of, money(it.target))
            } ?: ""
            prefs[savingsGoalPercentKey] = percentDisplay
            prefs[savingsGoalEtaKey] = etaDisplay
        }
        SavingsGoalWidget().update(context, glanceId)
    }
}

@Preview(widthDp = 180, heightDp = 120)
@Composable
private fun SavingsGoalWidgetPreview() {
    GlanceTheme {
        SavingsGoalWidget().SavingsGoalContent(
            hasGoal = true,
            savedDisplay = "$1,200",
            targetDisplay = "of $5,000",
            percentDisplay = "24%",
            etaDisplay = "On track for Mar 2027",
            label = "Savings goal",
            emptyLabel = "Set a savings goal in Analytics",
        )
    }
}
