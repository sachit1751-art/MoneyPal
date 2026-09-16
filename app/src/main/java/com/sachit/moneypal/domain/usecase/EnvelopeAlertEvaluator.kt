package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.domain.calculator.EnvelopeProgress
import com.sachit.moneypal.domain.model.Category
import java.math.BigDecimal
import javax.inject.Inject

/**
 * One pending envelope alert for a category: the threshold just transitioned
 * into (80% or 100% of the category's monthly envelope limit).
 */
data class EnvelopeAlert(
    val category: Category,
    val threshold: BudgetThreshold,
    val spent: BigDecimal,
    val limit: BigDecimal,
)

/**
 * Pure per-category envelope alert evaluation (plan 007). Reuses the
 * edge-triggered [BudgetThresholdEvaluator] — the same evaluator that powers
 * budget-level 80/100% alerts — applied to each category's
 * [EnvelopeProgress]. [lastAlertedFor] supplies the persisted per-category
 * (and per-period) alert state so re-evaluations never re-alert the same
 * level. Unit-tested in `EnvelopeAlertEvaluatorTest`.
 */
class EnvelopeAlertEvaluator @Inject constructor(
    private val thresholdEvaluator: BudgetThresholdEvaluator,
) {

    /**
     * @param progress Envelope progress for the current period (from
     *   [com.sachit.moneypal.domain.calculator.EnvelopeCalculator]).
     * @param lastAlertedFor Highest threshold already alerted for a category
     *   id, or null when none.
     */
    fun evaluate(
        progress: List<EnvelopeProgress>,
        lastAlertedFor: (Long) -> BudgetThreshold?,
    ): List<EnvelopeAlert> = progress.mapNotNull { p ->
        val next = thresholdEvaluator.evaluate(
            spent = p.spent.toDouble(),
            budget = p.limit.toDouble(),
            lastAlertedThreshold = lastAlertedFor(p.category.id),
        ) ?: return@mapNotNull null
        EnvelopeAlert(
            category = p.category,
            threshold = next,
            spent = p.spent,
            limit = p.limit,
        )
    }
}
