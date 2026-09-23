package com.sachit.moneypal.presentation.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import logcat.asLog
import logcat.logcat

/**
 * Plan 042: re-renders every money widget after the "hide amounts" setting
 * changes.
 *
 * Widgets read the live preference during composition (see [WidgetPrivacy]),
 * so no state rewrite is needed — forcing a fresh render per widget class is
 * enough for the toggle to take effect immediately. Failures are logged and
 * swallowed: a failed re-render leaves the previous rendering in place and
 * the next natural widget update picks the flag up anyway.
 */
class WidgetPrivacyRefresher @javax.inject.Inject constructor() {

    private fun widgetFactories(): List<GlanceAppWidget> = listOf(
        ExpenseWidget(),
        BudgetOverviewWidget(),
        CompleteBudgetWidget(),
        AverageSpendWidget(),
        MinMaxSpentWidget(),
        MonthHeatmapWidget(),
        SavingsGoalWidget(),
    )

    suspend fun refreshAll(context: Context) {
        widgetFactories().forEach { widget ->
            runCatching {
                widget.updateAll(context)
            }.onFailure { e ->
                logcat("SACHIT:WidgetPrivacy") {
                    "Re-render of ${widget.javaClass.simpleName} failed\n${e.asLog()}"
                }
            }
        }
    }
}
