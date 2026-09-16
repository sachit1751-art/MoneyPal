package com.sachit.moneypal.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class Transaction(
    val id: Long = 0,
    val amount: BigDecimal,
    val comment: String = "",
    val date: LocalDateTime?,
    val createdAt: Long = System.currentTimeMillis(),
    val clientGeneratedId: String? = null,
    val periodId: Long = 0L,
    val isDeleted: Boolean = false,
    val isRecurrent: Boolean = false,
    val recurrentFrequency: RecurrentFrequency? = null,
    val recurrentEndDate: LocalDateTime? = null,
    val subscriptionDay: Int? = null,
    val categoryId: Long? = null,
    val isCredit: Boolean = false,
    val isCreditPaid: Boolean = false,
    val isAdjustment: Boolean = false,
    val sourceTransactionId: Long? = null,
    /** Local content URI of an attached receipt photo, or null. */
    val attachmentUri: String? = null,
    /** Amount as entered in [originalCurrency]; null when the entry used the budget currency. */
    val originalAmount: BigDecimal? = null,
    /** ISO 4217 code of the currency the amount was originally entered in. */
    val originalCurrency: String? = null,
    /** True when the user expects this expense to be refunded. */
    val refundExpected: Boolean = false,
    /** Instant (epoch millis) the refund arrived; null until settled. */
    val refundedAt: Long? = null,
    /** Payment method used for this expense. */
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,
    /** True when this entry is income (money in); excluded from all spend math. */
    val isIncome: Boolean = false,
    /**
     * Instant (epoch millis) a recurring expense was paused; null when active.
     * Meaningful only when [isRecurrent] (plan 008).
     */
    val pausedAtEpochMs: Long? = null,
    /** Where this row came from: "manual", "sms", "wear"; null = legacy/manual (plan 014). */
    val source: String? = null,
    /** SMS capture confidence 0..100; null when [source] != "sms" (plan 014). */
    val captureConfidence: Int? = null,
) {
    /** True when this recurring expense is paused (reminders and due-today sums off). */
    val isRecurrentPaused: Boolean get() = isRecurrent && pausedAtEpochMs != null

    companion object {
        fun create(
            amount: BigDecimal,
            comment: String = "",
            date: LocalDateTime?,
            periodId: Long = 0L,
            clientGeneratedId: String? = null,
            isRecurrent: Boolean = false,
            recurrentFrequency: RecurrentFrequency? = null,
            recurrentEndDate: LocalDateTime? = null,
            subscriptionDay: Int? = null,
            categoryId: Long? = null,
            isCredit: Boolean = false,
            isCreditPaid: Boolean = false,
            isAdjustment: Boolean = false,
            attachmentUri: String? = null,
            originalAmount: BigDecimal? = null,
            originalCurrency: String? = null,
            refundExpected: Boolean = false,
            refundedAt: Long? = null,
            paymentMethod: PaymentMethod = PaymentMethod.OTHER,
            isIncome: Boolean = false,
            pausedAtEpochMs: Long? = null,
            source: String? = null,
            captureConfidence: Int? = null
        ): Transaction {
            require(!(isAdjustment && isIncome)) { "An adjustment cannot be income" }
            return Transaction(
                id = 0,
                amount = amount,
            comment = comment,
            date = date,
            periodId = periodId,
            clientGeneratedId = clientGeneratedId,
            isDeleted = false,
            isRecurrent = isRecurrent,
            recurrentFrequency = recurrentFrequency,
            recurrentEndDate = recurrentEndDate,
            subscriptionDay = subscriptionDay,
            categoryId = categoryId,
            isCredit = isCredit,
            isCreditPaid = isCreditPaid,
            isAdjustment = isAdjustment,
            attachmentUri = attachmentUri,
            originalAmount = originalAmount,
            originalCurrency = originalCurrency,
            refundExpected = refundExpected,
            refundedAt = refundedAt,
            paymentMethod = paymentMethod,
            isIncome = isIncome,
            pausedAtEpochMs = pausedAtEpochMs,
            source = source,
            captureConfidence = captureConfidence,
        )
        }
    }
}


enum class RecurrentFrequency {
    WEEKLY,
    BIWEEKLY,
    MONTHLY
}

/** How an expense was paid. Legacy entries default to [OTHER]. */
enum class PaymentMethod {
    CASH,
    CARD,
    OTHER
}

data class PaidRecurrentOccurrence(
    val transactionId: Long,
    val occurrenceDate: LocalDate,
    /** Epoch millis the occurrence was marked, or [SKIPPED_OCCURRENCE_MARKER] when skipped. */
    val paidAt: Long = 0,
) {
    /** True when this occurrence was skipped (not paid) — plan 008. */
    val isSkipped: Boolean get() = paidAt == SKIPPED_OCCURRENCE_MARKER
}

/** [PaidRecurrentOccurrence.paidAt] sentinel marking a skipped (not paid) occurrence. */
const val SKIPPED_OCCURRENCE_MARKER = -1L

/**
 * True when the set records the occurrence as settled — paid *or* skipped.
 *
 * Matches on (transactionId, occurrenceDate) only, mirroring the Room table's
 * primary key. [PaidRecurrentOccurrence.paidAt] must NOT matter here, otherwise
 * skipped occurrences (paidAt = [SKIPPED_OCCURRENCE_MARKER]) would never match a
 * default-constructed lookup and skip suppression would silently never work.
 */
fun Set<PaidRecurrentOccurrence>.containsOccurrence(
    transactionId: Long,
    occurrenceDate: LocalDate,
): Boolean = any { it.transactionId == transactionId && it.occurrenceDate == occurrenceDate }
