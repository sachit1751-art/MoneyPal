package com.sachit.moneypal.domain.sms

import java.math.BigDecimal

/**
 * A parsed bank-SMS transaction candidate. Pure data — no Android types — so the
 * parser stays unit-testable on the JVM.
 */
data class BankSmsMatch(
    val amount: BigDecimal,
    val isCredit: Boolean,
    val sender: String,
    val timestampMillis: Long,
    /** Raw numeric string as it appeared in the message (e.g. "1,234.56"), for logging. */
    val rawAmountText: String,
)
