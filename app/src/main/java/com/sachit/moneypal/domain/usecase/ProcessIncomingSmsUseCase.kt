package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.calculator.CategorySuggester
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.domain.sms.BankSmsMatch
import com.sachit.moneypal.domain.sms.BankSmsParser
import com.sachit.moneypal.domain.sms.SmsCaptureConfidence
import com.sachit.moneypal.domain.sms.SmsMerchantExtractor
import kotlinx.coroutines.flow.first
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
 * [SettingsRepository.markSmsSeen] (bounded 500-key set — see plan 012). The
 * dedupe write happens AFTER the insert succeeds so a crash between insert and
 * dedupe-mark re-inserts rather than loses, but a dedupe-mark failure is
 * swallowed: `Result.Error` is reserved for insert failures (which are safe to
 * retry because nothing was inserted).
 *
 * Period handling: the SMS carries its own timestamp, so period assignment
 * follows the SMS capture date (not `LocalDate.now()`) — a message delivered
 * after midnight lands in the period its transaction belongs to, and one
 * captured for a closed period is queued for the next period instead.
 */
class ProcessIncomingSmsUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
    private val categorySuggester: CategorySuggester,
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
            val captureDate = Instant.ofEpochMilli(match.timestampMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val isPastPeriodEnd =
                budgetSettings != null && captureDate.isAfter(budgetSettings.getPeriodEndDate())

            // Smart capture (plan 014): extract the merchant from the body,
            // suggest a category from history, score the capture confidence.
            val merchant = SmsMerchantExtractor.extract(match.body)
            val suggestedCategory = if (!match.isCredit) {
                runCatching {
                    val history = budgetRepository.getTransactions().first()
                    val categories = budgetRepository.getActiveCategories().first()
                    val index = categorySuggester.buildIndex(history)
                    categorySuggester.suggest(merchant ?: "", categories, index)
                }.getOrNull()
            } else {
                null
            }
            val confidence = SmsCaptureConfidence.score(
                senderLooksLikeBank = BankSmsParser.isLikelyBankSender(sender),
                merchantFound = merchant != null,
                categorySuggested = suggestedCategory != null,
                amount = match.amount,
            )

            val transaction = Transaction.create(
                amount = amount,
                comment = merchant ?: match.sender,
                date = eventTime,
                periodId = if (isPastPeriodEnd) 0L else getCurrentPeriodId(),
                isAdjustment = match.isCredit,
                categoryId = suggestedCategory?.category?.id,
                source = "sms",
                captureConfidence = confidence,
            )

            if (isPastPeriodEnd) {
                budgetRepository.addQueuedTransaction(transaction)
                logcat(TAG) { "Queued SMS ${match.sender} ${match.amount} for next period" }
            } else {
                budgetRepository.addTransaction(transaction)
                logcat(TAG) { "Captured SMS ${match.sender} ${match.amount}" }
            }

            // Mark seen only after a successful insert (re-delivery re-inserts rather
            // than loses). But a dedupe-mark failure must NOT fail the result: the
            // expense IS recorded, and the worker maps Result.Error to retry, which
            // would insert it a second time (plan 012).
            try {
                settingsRepository.markSmsSeen(dedupeKey)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logcat(TAG) { "markSmsSeen failed after insert (not retrying): ${e.message}" }
            }

            Result.Captured(
                transactionId = transaction.id,
                amount = match.amount,
                isCredit = match.isCredit,
                sender = match.sender,
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logcat(TAG) { "SMS capture failed: ${e.message}" }
            Result.Error("insert:${e.message ?: "Capture failed"}")
        }
    }

    private suspend fun getCurrentPeriodId(): Long =
        settingsRepository.getCurrentPeriodId().takeIf { it > 0L } ?: 0L

    private companion object {
        const val TAG = "SACHIT:SmsCapture"
    }
}
