package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.domain.sms.BankSmsMatch
import com.sachit.moneypal.domain.sms.BankSmsParser
import logcat.logcat
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Parses an incoming bank SMS and, when it matches a money message, records it:
 *
 * - debit  → normal expense (deducted from the budget), comment = sender
 * - credit → budget adjustment that adds money back (negative amount,
 *   `isAdjustment = true`) — the same semantics as a manual `+100` entry.
 *
 * Dedupe: `sender + minute-bucket + amount + direction` hash stored via
 * [SettingsRepository.markSmsSeen] (capped? no — but keys are tiny and per-minute
 * buckets; DataStore handles the size). The dedupe write happens AFTER the insert
 * succeeds so a crash between insert and dedupe-mark re-inserts rather than loses.
 *
 * Period handling mirrors [com.sachit.moneypal.presentation.ui.budget.BudgetTransactionHandler]:
 * when **today** is past the period end, the transaction is queued for the next
 * period instead (the SMS timestamp may lag; the period assignment follows the
 * day of capture, same as manual entry).
 */
class ProcessIncomingSmsUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
) {

    sealed interface Result {
        /** Inserted (or queued) — UI may show a capture notification with undo. */
        data class Captured(
            val transactionId: Long,
            val amount: BigDecimal,
            val isCredit: Boolean,
            val sender: String,
        ) : Result

        /** Not a money SMS, duplicate, or feature disabled. */
        data object Ignored : Result

        data class Error(val reason: String) : Result
    }

    suspend operator fun invoke(
        sender: String,
        body: String,
        timestampMillis: Long,
    ): Result {
        val settings = settingsRepository.getSettings()
        if (!settings.smsCaptureEnabled) {
            return Result.Ignored
        }

        val match: BankSmsMatch = BankSmsParser.parse(sender, body, timestampMillis)
            ?: return Result.Ignored

        val dedupeKey = BankSmsParser.dedupeKey(match)
        if (settingsRepository.isSmsSeen(dedupeKey)) {
            logcat(TAG) { "Duplicate SMS skipped: $dedupeKey" }
            return Result.Ignored
        }

        // Debit → expense (positive amount). Credit → adjustment (negated amount).
        val amount = if (match.isCredit) match.amount.negate() else match.amount
        val eventTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(match.timestampMillis),
            ZoneId.systemDefault(),
        )

        return try {
            val budgetSettings = budgetRepository.getBudgetSettingsSync()
            val isPastPeriodEnd =
                budgetSettings != null && LocalDate.now().isAfter(budgetSettings.getPeriodEndDate())

            val transaction = Transaction.create(
                amount = amount,
                comment = match.sender,
                date = eventTime,
                periodId = if (isPastPeriodEnd) 0L else getCurrentPeriodId(),
                isAdjustment = match.isCredit,
            )

            if (isPastPeriodEnd) {
                budgetRepository.addQueuedTransaction(transaction)
                logcat(TAG) { "Queued SMS ${match.sender} ${match.amount} for next period" }
            } else {
                budgetRepository.addTransaction(transaction)
                logcat(TAG) { "Captured SMS ${match.sender} ${match.amount}" }
            }

            // Mark seen only after a successful insert (re-delivery re-inserts rather than loses).
            settingsRepository.markSmsSeen(dedupeKey)

            Result.Captured(
                transactionId = transaction.id,
                amount = match.amount,
                isCredit = match.isCredit,
                sender = match.sender,
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logcat(TAG) { "SMS capture failed: ${e.message}" }
            Result.Error(e.message ?: "Capture failed")
        }
    }

    private suspend fun getCurrentPeriodId(): Long =
        settingsRepository.getCurrentPeriodId().takeIf { it > 0L } ?: 0L

    private companion object {
        const val TAG = "SACHIT:SmsCapture"
    }
}
