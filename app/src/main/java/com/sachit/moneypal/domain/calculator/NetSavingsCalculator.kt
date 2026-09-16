package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.Transaction
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Net savings for a set of transactions (plan 006): income minus expenses,
 * with income rows (flagged [Transaction.isIncome]) counted as money in and
 * everything else that counts as spend counted as money out. Pure and
 * unit-testable; the Analytics screen renders the result.
 */
data class NetSavings(
    val income: BigDecimal,
    val spent: BigDecimal,
    val net: BigDecimal,
)

class NetSavingsCalculator @Inject constructor() {

    /**
     * @param transactions Period transactions (already period-filtered by the
     *   caller). Soft-deleted rows and adjustments are ignored.
     */
    fun compute(transactions: List<Transaction>): NetSavings {
        val income = transactions
            .filter { it.isIncome && !it.isDeleted && !it.isAdjustment }
            .sumOf { it.amount }
        val spent = transactions
            .filter { !it.isIncome && !it.isDeleted && !it.isAdjustment }
            .sumOf { it.amount }
        return NetSavings(
            income = income,
            spent = spent,
            net = income.subtract(spent),
        )
    }
}
