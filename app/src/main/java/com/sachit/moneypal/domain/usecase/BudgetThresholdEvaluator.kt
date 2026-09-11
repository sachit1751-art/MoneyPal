package com.sachit.moneypal.domain.usecase

import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Which budget threshold triggered an alert.
 */
enum class BudgetThreshold {
    /** 80% of the budget consumed. */
    EIGHTY,

    /** 100% of the budget consumed (fully spent or overspent). */
    FULL,
}

/**
 * Pure evaluator deciding whether a spending-threshold alert should fire.
 *
 * Alerts are edge-triggered: they fire only on the transition into a threshold,
 * and [lastAlertedThreshold] is the highest threshold already alerted for the
 * current scope (day or period) so re-evaluations never re-alert the same level.
 *
 * unit-tested in `BudgetThresholdEvaluatorTest` — keep logic here, not in UI.
 */
class BudgetThresholdEvaluator @Inject constructor() {

    /**
     * @param spent Amount consumed (>= 0).
     * @param budget The budget being measured against (> 0 for alerts to be meaningful).
     * @param lastAlertedThreshold Highest threshold already alerted, or null if none.
     */
    fun evaluate(
        spent: Double,
        budget: Double,
        lastAlertedThreshold: BudgetThreshold?,
    ): BudgetThreshold? {
        if (budget <= 0.0 || spent < 0.0) return null
        val ratio = spent / budget
        val current = when {
            ratio >= 1.0 -> BudgetThreshold.FULL
            ratio >= 0.8 -> BudgetThreshold.EIGHTY
            else -> null
        }
        if (current == null) return null
        // Already alerted at this level or higher → don't re-alert.
        return if (lastAlertedThreshold == null || current.ordinal > lastAlertedThreshold.ordinal) {
            current
        } else {
            null
        }
    }

    /** Percentage of the budget consumed, as a whole number (0..∞). */
    fun percentConsumed(spent: Double, budget: Double): Int {
        if (budget <= 0.0) return 0
        return ((spent / budget) * 100).roundToInt()
    }
}
