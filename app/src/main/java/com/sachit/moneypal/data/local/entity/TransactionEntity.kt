package com.sachit.moneypal.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["clientGeneratedId"], unique = true),
        Index(value = ["categoryId"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: String,
    val comment: String,
    val date: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val clientGeneratedId: String? = null,
    val periodId: Long = 0L,
    val isRecurrent: Boolean = false,
    val recurrentFrequency: String? = null,
    val recurrentEndDate: Long? = null,
    val subscriptionDay: Int? = null,
    val categoryId: Long? = null,
    val isCredit: Boolean = false,
    val isCreditPaid: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isAdjustment: Boolean = false,
    @ColumnInfo(defaultValue = "NULL")
    val attachmentUri: String? = null,
    /** Amount as entered in [originalCurrency]; null when the entry used the budget currency. */
    @ColumnInfo(defaultValue = "NULL")
    val originalAmount: String? = null,
    /** ISO 4217 code of the currency the amount was originally entered in. */
    @ColumnInfo(defaultValue = "NULL")
    val originalCurrency: String? = null,
    /** True when the user expects this expense to be refunded. */
    @ColumnInfo(defaultValue = "0")
    val refundExpected: Boolean = false,
    /** Epoch millis when the refund arrived; null until settled. */
    @ColumnInfo(defaultValue = "NULL")
    val refundedAt: Long? = null,
    /** Payment method used (see [com.sachit.moneypal.domain.model.PaymentMethod]); legacy rows default to OTHER. */
    @ColumnInfo(defaultValue = "'OTHER'")
    val paymentMethod: String = "OTHER"
) {
    companion object {
        fun fromDomain(
            amount: String,
            comment: String,
            date: LocalDateTime,
            isRecurrent: Boolean = false,
            recurrentFrequency: String? = null,
            recurrentEndDate: LocalDateTime? = null,
            subscriptionDay: Int? = null,
            categoryId: Long? = null,
            isCredit: Boolean = false,
            isCreditPaid: Boolean = false,
            isAdjustment: Boolean = false,
            attachmentUri: String? = null,
            originalAmount: String? = null,
            originalCurrency: String? = null,
            paymentMethod: String = "OTHER"
        ): TransactionEntity = TransactionEntity(
            id = 0,
            amount = amount,
            comment = comment,
            date = date.toEpochSecond(ZoneOffset.UTC) * 1000,
            isRecurrent = isRecurrent,
            recurrentFrequency = recurrentFrequency,
            recurrentEndDate = recurrentEndDate?.toEpochSecond(ZoneOffset.UTC)?.times(1000),
            subscriptionDay = subscriptionDay,
            categoryId = categoryId,
            isCredit = isCredit,
            isCreditPaid = isCreditPaid,
            isAdjustment = isAdjustment,
            attachmentUri = attachmentUri,
            originalAmount = originalAmount,
            originalCurrency = originalCurrency,
            paymentMethod = paymentMethod
        )
    }
}
