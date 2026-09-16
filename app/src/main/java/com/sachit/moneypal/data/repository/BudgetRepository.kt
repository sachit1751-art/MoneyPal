package com.sachit.moneypal.data.repository

import com.sachit.moneypal.domain.model.BudgetSettings
import com.sachit.moneypal.domain.model.BudgetState
import com.sachit.moneypal.domain.model.Category
import com.sachit.moneypal.domain.model.PaidRecurrentOccurrence
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.domain.model.ArchivedBudget
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

interface BudgetRepository {

    fun getTransactions(): Flow<List<Transaction>>

    fun getQueuedTransactions(): Flow<List<Transaction>>

    fun getTransactionsForPeriod(start: LocalDate, end: LocalDate): Flow<List<Transaction>>

    suspend fun addTransaction(transaction: Transaction)

    suspend fun addQueuedTransaction(transaction: Transaction)

    suspend fun addTransactionIfAbsent(transaction: Transaction): Boolean

    suspend fun updateTransaction(transaction: Transaction)

    suspend fun assignQueuedTransactionsToPeriod(periodId: Long)

    suspend fun upsertTransactions(transactions: List<Transaction>)

    suspend fun existsTransactionByClientGeneratedId(clientGeneratedId: String): Boolean

    suspend fun getRecentTransactions(limit: Int): List<Transaction>

    suspend fun deleteTransaction(transaction: Transaction)

    fun getSpentForDate(date: LocalDate): Flow<BigDecimal>

    fun getSpentForPeriod(start: LocalDate, end: LocalDate): Flow<BigDecimal>

    fun getBudgetSettings(): Flow<BudgetSettings?>

    suspend fun saveBudgetSettings(settings: BudgetSettings)

    suspend fun getBudgetSettingsSync(): BudgetSettings?

    suspend fun getTransactionById(transactionId: Long): Transaction?

    /** Returns an existing transaction with the same amount+comment on [day], or null. */
    suspend fun findDuplicateTransaction(amount: BigDecimal, comment: String, day: LocalDate): Transaction?

    fun calculateBudgetState(settings: BudgetSettings, currentDate: LocalDate): Flow<BudgetState>

    fun getActiveCategories(): Flow<List<Category>>

    fun getAllCategories(): Flow<List<Category>>

    suspend fun findOrCreateCategory(name: String): Category

    suspend fun hideCategory(name: String)

    /** Sets (or clears, with nulls) the emoji/color avatar and monthly envelope limit of a category. */
    suspend fun setCategoryStyle(categoryId: Long, emoji: String?, colorArgb: String?, monthlyLimit: java.math.BigDecimal? = null)

    /** Marks an expense as expecting a refund (clears any refund timestamp). */
    suspend fun setRefundExpected(transactionId: Long, expected: Boolean)

    /** Marks a refund as received; clears the pending flag and stamps the time. */
    suspend fun markRefunded(transactionId: Long)

    suspend fun incrementCategoryUsage(name: String)

    suspend fun getPeriodCount(): Int

    suspend fun markCreditTransactionsAsPaid(start: LocalDate, end: LocalDate)

    suspend fun markAllCreditTransactionsAsPaid()

    suspend fun markTransactionAsPaid(transactionId: Long)

    fun getPaidRecurrentOccurrences(): Flow<Set<PaidRecurrentOccurrence>>

    suspend fun markRecurrentOccurrencePaid(transactionId: Long, occurrenceDate: LocalDate)

    suspend fun getPaidOccurrenceDatesFor(transactionId: Long): Set<LocalDate>

    /** Marks the occurrence on [occurrenceDate] as skipped (not billed) — plan 008. */
    suspend fun markOccurrenceSkipped(transactionId: Long, occurrenceDate: LocalDate)

    /** Pauses or resumes a recurring expense (plan 008); paused rows are never due. */
    suspend fun setRecurringPaused(transactionId: Long, paused: Boolean)

    fun getArchivedBudgets(): Flow<List<ArchivedBudget>>

    suspend fun upsertArchivedBudgets(archivedBudgets: List<ArchivedBudget>)

    /**
     * Inserts or replaces categories wholesale (backup restore path). Unlike
     * [findOrCreateCategory], preserves the provided usageCount/isHidden.
     */
    suspend fun upsertCategories(categories: List<Category>)

    /** Returns every transaction including soft-deleted rows (backup path). */
    suspend fun getAllTransactionsIncludingDeleted(): List<Transaction>

    suspend fun archiveCurrentPeriod(
        periodId: Long,
        settings: BudgetSettings,
        spentAmount: BigDecimal
    )

    suspend fun deleteArchivedBudget(periodId: Long)
}
