package com.sachit.moneypal.presentation.notification

import android.content.Context
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.usecase.BudgetThreshold
import com.sachit.moneypal.domain.usecase.EnvelopeAlertEvaluator
import com.sachit.moneypal.presentation.ui.budget.BudgetStateCalculator
import com.sachit.moneypal.presentation.util.font.format.symbolOnlyCurrencyFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches category envelope progress and fires per-category alerts (80% /
 * 100% of the monthly limit, plan 007) on the transition into each threshold —
 * the category-level counterpart of [ThresholdAlertObserver].
 *
 * Edge-triggered: the last-alerted threshold is persisted per category and per
 * period (`envelope_alerted_<categoryId>_<periodId>`), so re-evaluations never
 * re-alert the same level and a new period resets alerts naturally. The enable
 * toggle lives in Settings; the watcher only runs while it is on.
 */
@Singleton
class EnvelopeAlertObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper,
    private val evaluator: EnvelopeAlertEvaluator,
    private val budgetStateCalculator: BudgetStateCalculator,
) {

    companion object {
        private const val ALERTED_KEY_PREFIX = "envelope_alerted_"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watcher: Job? = null

    /**
     * Starts observing the alerts toggle; spawns or cancels the envelope
     * watcher as it flips. Call once from app start.
     */
    fun start() {
        scope.launch {
            settingsRepository.observeEnvelopeAlertsEnabled()
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    logcat { "EnvelopeAlertObserver enabled=$enabled" }
                    watcher?.cancel()
                    watcher = if (enabled) scope.launch { watchEnvelopes() } else null
                }
        }
    }

    private suspend fun watchEnvelopes() {
        combine(
            budgetRepository.getActiveCategories(),
            budgetRepository.getTransactions(),
            budgetRepository.getBudgetSettings(),
        ) { categories, transactions, settings ->
            Triple(categories, transactions, settings)
        }.collectLatest { (categories, transactions, settings) ->
            if (settings == null || categories.none { it.monthlyLimit != null }) {
                return@collectLatest
            }
            val currentPeriodId = settingsRepository.getCurrentPeriodId()
            val currentPeriodStartedAt = settingsRepository.getSettings().currentPeriodStartedAt
            val periodTransactions = budgetStateCalculator.filterPeriodTransactions(
                transactions = transactions,
                settings = settings,
                currentPeriodId = currentPeriodId,
                currentPeriodStartedAtMillis = currentPeriodStartedAt,
            )

            val progress = com.sachit.moneypal.domain.calculator.EnvelopeCalculator().compute(
                transactions = periodTransactions,
                categories = categories,
            )

            // Preload last-alerted state: the evaluator's lookup lambda is
            // non-suspending, so the DataStore reads happen up front.
            val lastAlertedByCategory = progress.associate { p ->
                p.category.id to settingsRepository
                    .getString(alertedKey(p.category.id, currentPeriodId))
                    ?.toBudgetThreshold()
            }
            val alerts = evaluator.evaluate(progress) { categoryId ->
                lastAlertedByCategory[categoryId]
            }

            val format = symbolOnlyCurrencyFormat(settings.currencyCode)
            alerts.forEach { alert ->
                settingsRepository.setString(
                    alertedKey(alert.category.id, currentPeriodId),
                    alert.threshold.name,
                )
                notificationHelper.showEnvelopeAlertNotification(
                    categoryName = alert.category.name,
                    thresholdPercent = when (alert.threshold) {
                        BudgetThreshold.EIGHTY -> 80
                        BudgetThreshold.FULL -> 100
                    },
                    spentFormatted = format.format(alert.spent),
                    limitFormatted = format.format(alert.limit),
                )
            }
        }
    }

    private fun alertedKey(categoryId: Long, periodId: Long) =
        "$ALERTED_KEY_PREFIX${categoryId}_$periodId"

    private fun String.toBudgetThreshold(): BudgetThreshold? = try {
        BudgetThreshold.valueOf(this)
    } catch (_: Exception) {
        null
    }
}
