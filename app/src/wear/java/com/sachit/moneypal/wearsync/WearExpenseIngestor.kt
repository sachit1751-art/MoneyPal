package com.sachit.moneypal.wearsync

import logcat.logcat
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.sync.contract.ExpensePayload
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearExpenseIngestor @Inject constructor(
    private val repository: BudgetRepository
) {

    companion object {
    }

    private val ingestMutex = Mutex()

    sealed interface IngestResult {
        data object Ok : IngestResult
        data class Error(val reason: String) : IngestResult
    }

    suspend fun ingest(payload: ExpensePayload): IngestResult = ingestMutex.withLock {
        if (payload.amount.isBlank()) {
            return@withLock IngestResult.Error("Amount empty")
        }

        val amount = runCatching { BigDecimal(payload.amount) }.getOrNull()
            ?: return@withLock IngestResult.Error("Invalid amount")

        val exists = repository.existsTransactionByClientGeneratedId(payload.clientGeneratedId)
        if (exists) {
            logcat { "ingest: duplicate pre-check id=${payload.clientGeneratedId}" }
            return@withLock IngestResult.Ok
        }

        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(payload.eventTime), ZoneId.systemDefault())

        val categoryId: Long? = if (payload.comment.isNotBlank()) {
            repository.findOrCreateCategory(payload.comment.trim()).id
        } else null

        // Mirror the manual-entry rule in BudgetTransactionHandler: past the period
        // end, queue the expense for the next period instead of inserting it into
        // the (stale) period carried in the payload.
        val settings = repository.getBudgetSettingsSync()
        val isPastPeriodEnd = settings != null && LocalDate.now().isAfter(settings.getPeriodEndDate())

        val tx = Transaction.create(
            amount = amount,
            comment = payload.comment,
            date = date,
            periodId = if (isPastPeriodEnd) 0L else payload.periodId ?: 0L,
            clientGeneratedId = payload.clientGeneratedId,
            categoryId = categoryId
        )

        if (isPastPeriodEnd) {
            repository.addQueuedTransaction(tx)
            logcat { "ingest: queued for next period" }
            return@withLock IngestResult.Ok
        }

        val inserted = repository.addTransactionIfAbsent(tx)
        if (!inserted) {
            logcat { "ingest: duplicate on insert-ignore id=${payload.clientGeneratedId}" }
        } else {
            logcat { "ingest: inserted id=${payload.clientGeneratedId}, amount=${payload.amount}" }
        }

        IngestResult.Ok
    }
}
