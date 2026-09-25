package com.sachit.moneypal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sachit.moneypal.data.local.entity.WalletBalanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletBalanceDao {

    @Query("SELECT * FROM wallet_balance WHERE id = 1")
    fun observe(): Flow<WalletBalanceEntity?>

    @Query("SELECT * FROM wallet_balance WHERE id = 1")
    suspend fun get(): WalletBalanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WalletBalanceEntity)

    @Query("DELETE FROM wallet_balance")
    suspend fun clear()
}
