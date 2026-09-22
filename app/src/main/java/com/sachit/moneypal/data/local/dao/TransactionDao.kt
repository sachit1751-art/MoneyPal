package com.sachit.moneypal.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sachit.moneypal.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions 
        WHERE date >= :startDate AND date < :endDate 
        ORDER BY date DESC
    """)
    fun getTransactionsForDateRange(
        startDate: Long,
        endDate: Long
    ): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE date >= :startOfDay AND date < :endOfDay 
        ORDER BY date DESC
    """)
    fun getTransactionsForDay(
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<TransactionEntity>>

    @Query("""
        SELECT amount FROM transactions 
        WHERE date >= :startOfDay AND date < :endOfDay
    """)
    fun getAmountsForDay(
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<String>>

    @Query("""
        SELECT amount FROM transactions 
        WHERE date >= :startDate AND date < :endDate
    """)
    fun getAmountsForPeriod(
        startDate: Long,
        endDate: Long
    ): Flow<List<String>>

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllOrReplace(transactions: List<TransactionEntity>): List<Long>

    @Query("SELECT id FROM transactions WHERE clientGeneratedId = :clientGeneratedId LIMIT 1")
    suspend fun findIdByClientGeneratedId(clientGeneratedId: String): Long?

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteById(transactionId: Long)

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: Long): TransactionEntity?

    @Query("SELECT COUNT(DISTINCT periodId) FROM transactions WHERE periodId > 0")
    suspend fun countDistinctPeriods(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE periodId = :periodId)")
    suspend fun hasTransactionsInPeriod(periodId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE clientGeneratedId = :clientGeneratedId)")
    suspend fun existsByClientGeneratedId(clientGeneratedId: String): Boolean

    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentTransactions(limit: Int): List<TransactionEntity>

    /** Low-confidence SMS captures awaiting review (plan 015). LIMIT keeps the dialog light. */
    @Query(
        "SELECT * FROM transactions WHERE source = 'sms' AND captureConfidence IS NOT NULL " +
            "AND captureConfidence < :threshold ORDER BY createdAt DESC LIMIT 50"
    )
    fun observeSmsReviews(threshold: Int): Flow<List<TransactionEntity>>

    /** User confirmed a low-confidence SMS capture (plan 015). */
    @Query("UPDATE transactions SET captureConfidence = 100 WHERE id = :transactionId")
    suspend fun confirmSmsCapture(transactionId: Long)

    /** Expenses the user expects to be refunded, not yet settled (plan 017). */
    @Query(
        "SELECT * FROM transactions WHERE refundExpected = 1 AND refundedAt IS NULL " +
            "ORDER BY date DESC"
    )
    fun observePendingRefunds(): Flow<List<TransactionEntity>>

    /** Marks the refund as received at [atMillis] on the ORIGINAL row (plan 017). */
    @Query("UPDATE transactions SET refundedAt = :atMillis WHERE id = :transactionId")
    suspend fun markRefundReceived(transactionId: Long, atMillis: Long)

    @Query("""
        UPDATE transactions 
        SET isCreditPaid = 1 
        WHERE isCredit = 1 AND date >= :startDate AND date < :endDate AND isCreditPaid = 0
    """)
    suspend fun markCreditAsPaidInRange(startDate: Long, endDate: Long)

    @Query("""
        UPDATE transactions 
        SET isCreditPaid = 1 
        WHERE isCredit = 1 AND isCreditPaid = 0
    """)
    suspend fun markAllCreditAsPaid()

    @Query("""
        UPDATE transactions 
        SET isCreditPaid = 1 
        WHERE id = :transactionId AND isCredit = 1
    """)
    suspend fun markTransactionAsPaid(transactionId: Long)

    /**
     * Duplicate candidates: same comment within one day, newest first
     * (day encoded the same way as stored dates — local wall-clock as
     * UTC millis). Amount equality is decided by the caller with BigDecimal
     * — TEXT-vs-REAL affinity float equality silently missed rows (plan 024).
     */
    @Query("""
        SELECT * FROM transactions
        WHERE comment = :comment
            AND date >= :startOfDay AND date < :endOfDay
        ORDER BY date DESC LIMIT 50
    """)
    suspend fun findByCommentAndDay(
        comment: String,
        startOfDay: Long,
        endOfDay: Long,
    ): List<TransactionEntity>

    @Query("UPDATE transactions SET refundExpected = :expected, refundedAt = NULL WHERE id = :transactionId")
    suspend fun setRefundExpected(transactionId: Long, expected: Boolean)

    @Query("UPDATE transactions SET refundExpected = 0, refundedAt = :refundedAt WHERE id = :transactionId")
    suspend fun markRefunded(transactionId: Long, refundedAt: Long = System.currentTimeMillis())

    /** Pauses (stamps the time) or resumes (clears) a recurring expense — plan 008. */
    @Query("UPDATE transactions SET pausedAtEpochMs = :pausedAt WHERE id = :transactionId")
    suspend fun setPaused(transactionId: Long, pausedAt: Long?)
}
