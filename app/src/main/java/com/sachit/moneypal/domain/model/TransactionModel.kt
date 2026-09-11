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
    val originalCurrency: String? = null
) {
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
            originalCurrency: String? = null
        ): Transaction = Transaction(
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
            originalCurrency = originalCurrency
        )
    }
}


enum class RecurrentFrequency {
    WEEKLY,
    BIWEEKLY,
    MONTHLY
}

data class PaidRecurrentOccurrence(
    val transactionId: Long,
    val occurrenceDate: LocalDate,
)
