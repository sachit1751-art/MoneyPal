package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.Category
import com.sachit.moneypal.domain.model.Transaction
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Progress of spending against a category's monthly envelope limit
 * (see [Category.monthlyLimit]).
 */
data class EnvelopeProgress(
    val category: Category,
    val limit: BigDecimal,
    /** Positive spend attributed to this category in the current period. */
    val spent: BigDecimal,
    val remaining: BigDecimal,
    /** [spent] / [limit], clamped to 1.0 for overspend so bars don't overflow. */
    val fraction: Float,
    val isOverBudget: Boolean,
)

/**
 * Pure calculator for category envelope budgets: attributes positive,
 * non-adjustment spend to categories and compares it against each category's
 * optional [Category.monthlyLimit]. Categories without a limit are ignored.
 */
class EnvelopeCalculator @Inject constructor() {

    fun compute(
        transactions: List<Transaction>,
        categories: List<Category>,
    ): List<EnvelopeProgress> {
        val limits = categories.filter { it.monthlyLimit != null && it.monthlyLimit.signum() > 0 }
        if (limits.isEmpty()) return emptyList()
        val limitById = limits.associateBy { it.id }

        val spentById = HashMap<Long, BigDecimal>()
        for (transaction in transactions) {
            val categoryId = transaction.categoryId ?: continue
            if (!limitById.containsKey(categoryId)) continue
            val amount = transaction.amount
            if (amount.signum() <= 0) continue
            if (transaction.isDeleted || transaction.isAdjustment || transaction.isIncome) continue
            spentById.merge(categoryId, amount, BigDecimal::add)
        }

        return limits.map { category ->
            val limit = category.monthlyLimit ?: BigDecimal.ZERO
            val spent = spentById[category.id] ?: BigDecimal.ZERO
            val isOver = spent > limit
            val fraction = if (limit.signum() == 0) {
                1f
            } else {
                (spent.divide(limit, 4, java.math.RoundingMode.HALF_UP)).toFloat().coerceAtMost(1f)
            }
            EnvelopeProgress(
                category = category,
                limit = limit,
                spent = spent,
                remaining = (limit - spent).max(BigDecimal.ZERO),
                fraction = fraction,
                isOverBudget = isOver,
            )
        }
    }

    companion object {
        /** Warn threshold for the progress bar color (≥90% of the limit). */
        const val WARN_FRACTION = 0.9f
    }
}
