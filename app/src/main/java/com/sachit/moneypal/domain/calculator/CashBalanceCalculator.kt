package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.PaymentMethod
import com.sachit.moneypal.domain.model.Transaction
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Pure calculator for the cash/wallet balance (plan 043). The balance is
 * anchored by a user-set starting value; every CASH expense subtracts, every
 * CASH income adds.
 *
 * Adjustment semantics (documented product decision): an **adjustment
 * replaces the running total** with its amount. Adjustments are the user's
 * "re-sync my wallet to what it really holds" tool, so they act as a
 * set-to-value, not an increment.
 */
class CashBalanceCalculator @Inject constructor() {

    /**
     * Returns the current balance, or null when no starting balance was set
     * (feature not configured). Only rows with [PaymentMethod.CASH] and
     * `!isDeleted` participate; rows are sorted ascending by date for
     * deterministic adjustment semantics.
     */
    fun currentBalance(
        starting: BigDecimal?,
        cashTransactions: List<Transaction>,
    ): BigDecimal? {
        val start = starting ?: return null

        val cash = cashTransactions
            .filter { it.paymentMethod == PaymentMethod.CASH && !it.isDeleted }
            .sortedWith(compareBy { it.date ?: LocalDateTime.MAX })

        var running = start
        for (tx in cash) {
            running = when {
                tx.isAdjustment -> tx.amount
                tx.isIncome -> running.add(tx.amount.abs())
                else -> running.subtract(tx.amount)
            }
        }
        return running
    }
}
