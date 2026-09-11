package com.sachit.moneypal.presentation.notification

import android.content.Context
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.usecase.BudgetThreshold
import com.sachit.moneypal.domain.usecase.BudgetThresholdEvaluator
import com.sachit.moneypal.presentation.util.font.format.symbolOnlyCurrencyFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import logcat.logcat
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches daily and period spending and fires budget-threshold alerts
 * (80% / 100%) on the transition into each threshold. Alerts are
 * edge-triggered and date/period-scoped via [SettingsRepository] keys, so
 * re-evaluations never re-alert the same level.
 *
 * The enable toggle lives in Settings; watchers only run while it is on.
 */
@Singleton
class ThresholdAlertObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper,
    private val evaluator: BudgetThresholdEvaluator,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchers: Job? = null

    /**
     * Starts observing the alerts toggle; spawns or cancels the spending
     * watchers as it flips. Call once from app start.
     */
    fun start() {
        scope.launch {
            settingsRepository.observeThresholdAlertsEnabled()
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    logcat { "ThresholdAlertObserver enabled=$enabled" }
                    watchers?.cancel()
                    watchers = if (enabled) {
                        scope.launch {
                            launch { watchDaily() }
                            launch { watchPeriod() }
                        }
                    } else {
                        null
                    }
                }
        }
    }

    private suspend fun watchDaily() {
        val today = LocalDate.now()
        budgetRepository.getSpentForDate(today)
            .map { spent -> spent to settingsRepository.getDailyAlertedThreshold(today.toEpochDay()) }
            .distinctUntilChanged()
            .collect { (spent, last) ->
                val settings = budgetRepository.getBudgetSettingsSync() ?: return@collect
                val dailyBudget = settings.calculateDailyBudget()
                val next = evaluator.evaluate(
                    spent.toDouble(), dailyBudget.toDouble(), last
                ) ?: return@collect
                settingsRepository.setDailyAlertedThreshold(next, today.toEpochDay())
                notify(
                    threshold = next,
                    isDaily = true,
                    spent = spent,
                    budget = dailyBudget,
                    currency = settings.currencyCode,
                )
            }
    }

    private suspend fun watchPeriod() {
        budgetRepository.getBudgetSettings().collectLatest { settings ->
            if (settings == null) return@collectLatest
            budgetRepository.getSpentForPeriod(settings.startDate, settings.getPeriodEndDate())
                .map { spent -> spent to settingsRepository.getPeriodAlertedThreshold() }
                .distinctUntilChanged()
                .collect { (spent, last) ->
                    val next = evaluator.evaluate(
                        spent.toDouble(), settings.totalBudget.toDouble(), last
                    ) ?: return@collect
                    settingsRepository.setPeriodAlertedThreshold(next)
                    notify(
                        threshold = next,
                        isDaily = false,
                        spent = spent,
                        budget = settings.totalBudget,
                        currency = settings.currencyCode,
                    )
                }
        }
    }

    private fun notify(
        threshold: BudgetThreshold,
        isDaily: Boolean,
        spent: BigDecimal,
        budget: BigDecimal,
        currency: String,
    ) {
        val percent = when (threshold) {
            BudgetThreshold.EIGHTY -> 80
            BudgetThreshold.FULL -> 100
        }
        val format = symbolOnlyCurrencyFormat(currency)
        notificationHelper.showThresholdAlertNotification(
            scope = if (isDaily) "daily" else "period",
            thresholdPercent = percent,
            spentFormatted = format.format(spent),
            budgetFormatted = format.format(budget),
        )
    }
}
