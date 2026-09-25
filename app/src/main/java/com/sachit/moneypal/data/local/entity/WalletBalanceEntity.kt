package com.sachit.moneypal.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row (always `id = 1`) holding the user's cash/wallet starting
 * balance (plan 043). `startingBalance` is a BigDecimal plain string and null
 * until the user first sets it — null means "no cash tracking", so the whole
 * feature stays hidden.
 */
@Entity(tableName = "wallet_balance")
data class WalletBalanceEntity(
    @PrimaryKey val id: Int = 1,
    /** Starting balance as a BigDecimal plain string; null until first setup. */
    val startingBalance: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
