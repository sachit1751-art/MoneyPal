package com.sachit.moneypal.presentation.ui.history

import com.sachit.moneypal.domain.model.Transaction
import java.math.BigDecimal
import java.util.Locale

/**
 * Ephemeral search/filter state for the History screen. Deliberately NOT part
 * of [com.sachit.moneypal.domain.model.UserSettings] — it is UI state, not a
 * user preference, and resets when the screen is recreated.
 */
data class HistoryFilterState(
    val query: String = "",
    val categoryName: String? = null,
    val minAmount: BigDecimal? = null,
    val maxAmount: BigDecimal? = null,
    val recurrentOnly: Boolean = false,
    val creditOnly: Boolean = false,
) {
    val isActive: Boolean
        get() = query.isNotBlank() ||
            categoryName != null ||
            minAmount != null ||
            maxAmount != null ||
            recurrentOnly ||
            creditOnly
}

/**
 * Pure, side-effect-free filtering of transactions for the History screen.
 *
 * Rules:
 * - [HistoryFilterState.query] matches (case-insensitive) when it is contained
 *   in the comment, the category name (via [categoryNames]), or the plain
 *   string of the amount (e.g. "450" matches 450.00).
 * - [HistoryFilterState.categoryName] matches transactions whose category name
 *   (resolved via [categoryNames]) equals it exactly — chips toggle by name.
 * - [HistoryFilterState.minAmount] / [HistoryFilterState.maxAmount] compare
 *   against the absolute amount so income entries remain searchable.
 * - [HistoryFilterState.recurrentOnly] / [HistoryFilterState.creditOnly]
 *   require the respective flag.
 *
 * This function intentionally does NOT filter soft-deleted transactions —
 * the pending-removed undo flow relies on recently-removed items staying
 * renderable. Callers pass already period-scoped lists.
 */
internal fun filterTransactions(
    transactions: List<Transaction>,
    filter: HistoryFilterState,
    categoryNames: Map<Long, String>,
): List<Transaction> {
    if (!filter.isActive) return transactions

    val query = filter.query.trim().lowercase(Locale.ROOT)
    return transactions.filter { transaction ->
        if (filter.categoryName != null &&
            categoryNames[transaction.categoryId] != filter.categoryName
        ) {
            return@filter false
        }
        if (filter.recurrentOnly && !transaction.isRecurrent) return@filter false
        if (filter.creditOnly && !transaction.isCredit) return@filter false

        val absAmount = transaction.amount.abs()
        if (filter.minAmount != null && absAmount < filter.minAmount) return@filter false
        if (filter.maxAmount != null && absAmount > filter.maxAmount) return@filter false

        if (query.isNotEmpty()) {
            val matchesComment = transaction.comment.lowercase(Locale.ROOT).contains(query)
            val categoryName = transaction.categoryId?.let { categoryNames[it] }.orEmpty()
            val matchesCategory = categoryName.lowercase(Locale.ROOT).contains(query)
            val matchesAmount = transaction.amount.toPlainString().contains(query)
            if (!matchesComment && !matchesCategory && !matchesAmount) return@filter false
        }

        true
    }
}
