package com.sachit.moneypal.data.repository

import com.sachit.moneypal.data.local.dao.WalletBalanceDao
import com.sachit.moneypal.data.local.entity.WalletBalanceEntity
import com.sachit.moneypal.domain.calculator.CashBalanceCalculator
import com.sachit.moneypal.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed access to the cash/wallet starting balance (plan 043) plus the
 * derived current balance, computed with [CashBalanceCalculator] over the
 * transaction stream.
 */
@Singleton
class WalletBalanceRepository @Inject constructor(
    private val walletBalanceDao: WalletBalanceDao,
    private val cashBalanceCalculator: CashBalanceCalculator,
) {

    /** Starting balance as set by the user; null when never configured. */
    fun observeStartingBalance(): Flow<BigDecimal?> =
        walletBalanceDao.observe().map { it?.startingBalance?.toBigDecimalOrNull() }

    /**
     * Current cash balance: starting balance adjusted by every CASH
     * transaction. Null when the feature is not configured.
     */
    fun observeBalance(transactions: Flow<List<Transaction>>): Flow<BigDecimal?> =
        kotlinx.coroutines.flow.combine(walletBalanceDao.observe(), transactions) { row, txs ->
            cashBalanceCalculator.currentBalance(
                starting = row?.startingBalance?.toBigDecimalOrNull(),
                cashTransactions = txs,
            )
        }

    suspend fun setStartingBalance(value: BigDecimal) {
        walletBalanceDao.upsert(WalletBalanceEntity(startingBalance = value.toPlainString()))
    }

    /** Clears the starting balance — turns the feature off. */
    suspend fun clearStartingBalance() {
        walletBalanceDao.clear()
    }
}
