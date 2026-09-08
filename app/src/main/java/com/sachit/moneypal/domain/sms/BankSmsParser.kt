package com.sachit.moneypal.domain.sms

import java.math.BigDecimal
import java.util.Locale

/**
 * Generic regex parser for bank/payment SMS texts. Pure Kotlin — unit tested in
 * `BankSmsParserTest`.
 *
 * Design (decided 2026-09-08): a smart generic parser that covers the common
 * money-SMS shapes rather than per-bank templates:
 *  - amounts like "Rs 500", "Rs.1,234.56", "INR 250.75", "₹250", "$12.34",
 *    "12,500.00 Rs"
 *  - debit keywords: debited, spent, paid, withdrawn, deducted, purchased,
 *    "sent", "transferred"
 *  - credit keywords: credited, received, refund, deposited, cashback, "added"
 *  - OTPs, promotions and balance-only messages produce NO match (no keyword +
 *    amount pair).
 *
 * Amount selection: real bank SMS put the amount *before* the keyword
 * ("Rs 1,234.56 debited from a/c XX99") or *after* it ("debited for Rs 500"),
 * so the whole body is scanned and the currency-qualified amount **closest to
 * the keyword occurrence** wins. Amounts without a currency cue (account
 * numbers, reference IDs, balances in unmarked formats) are never candidates.
 * Messages containing BOTH a debit and a credit keyword are rejected as
 * ambiguous.
 */
object BankSmsParser {

    private const val NUMBER = """(?:\d{1,3}(?:,\d{2,3})*|\d+)(?:\.\d{1,4})?"""

    /** amount with a currency cue before or after: Rs/Rs./INR/₹/$/€/£/Rs-suffix forms */
    private val CURRENCY_AMOUNT = Regex(
        pattern =
            "(?:(?:Rs\\.?|INR|₹|\\$|€|£)\\s?($NUMBER)|($NUMBER)\\s?(?:Rs\\.?|INR|₹))",
        RegexOption.IGNORE_CASE,
    )

    private val DEBIT_KEYWORD = Regex(
        "(?:\\b(?:debited|debit|spent|paid|withdrawn|withdraw|deducted|deduct|purchased|purchase|sent|transferred|transfer)\\b)",
        RegexOption.IGNORE_CASE,
    )

    private val CREDIT_KEYWORD = Regex(
        "(?:\\b(?:credited|credit|received|recd|recv|refund|refunded|deposited|deposit|cashback|cash_back|added)\\b)",
        RegexOption.IGNORE_CASE,
    )

    private val OTP_NOISE = Regex(
        "(?i)\\b(?:otp|one\\s?time\\s?password|password|verification\\s?code)\\b"
    )

    /** Parse [body] from [sender]. Returns null when the message is not a money SMS. */
    fun parse(sender: String, body: String, timestampMillis: Long): BankSmsMatch? {
        if (body.isBlank()) return null

        // OTPs and verification messages are never transactions, even if an amount
        // appears ("OTP 123456 to debit Rs 500") — bail out before keyword matching.
        if (OTP_NOISE.containsMatchIn(body)) return null

        val isDebit = DEBIT_KEYWORD.containsMatchIn(body)
        val isCredit = CREDIT_KEYWORD.containsMatchIn(body)
        if (isDebit == isCredit) return null // neither, or both → ambiguous

        val keywordMatch = (if (isDebit) DEBIT_KEYWORD else CREDIT_KEYWORD).find(body) ?: return null
        val amountText = findAmountClosestToKeyword(body, keywordMatch.range) ?: return null

        val value = parseAmount(amountText) ?: return null
        if (value <= BigDecimal.ZERO) return null

        return BankSmsMatch(
            amount = value,
            isCredit = isCredit,
            sender = sender.trim(),
            timestampMillis = timestampMillis,
            rawAmountText = amountText,
        )
    }

    /**
     * Scan the whole [body] for currency-qualified amounts and return the one
     * closest to the keyword occurrence at [keywordRange]. Handles both
     * "Rs 500 debited" (amount before) and "debited Rs 500" (amount after).
     */
    private fun findAmountClosestToKeyword(body: String, keywordRange: IntRange): String? {
        return CURRENCY_AMOUNT.findAll(body)
            .mapNotNull { match ->
                val text = match.groupValues[1].ifBlank { match.groupValues[2] }
                if (text.isBlank()) return@mapNotNull null
                val distance = if (match.range.first > keywordRange.last) {
                    match.range.first - keywordRange.last // amount after keyword
                } else {
                    keywordRange.first - match.range.last // amount before keyword
                }
                distance to text
            }
            .minByOrNull { it.first }
            ?.second
    }

    /** "1,234.56" → 1234.56; locale-tolerant: strips commas, spaces, NBSP. */
    internal fun parseAmount(raw: String): BigDecimal? {
        val cleaned = raw.replace(",", "").replace("\u00A0", "").trim()
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()
    }

    /** Stable dedupe key: sender + wall-clock minute + amount + direction. */
    fun dedupeKey(match: BankSmsMatch): String {
        val minuteBucket = match.timestampMillis / 60_000L
        val direction = if (match.isCredit) "C" else "D"
        return String.format(
            Locale.ROOT,
            "sms|%s|%d|%s|%s",
            match.sender.lowercase(Locale.ROOT),
            minuteBucket,
            match.amount.toPlainString(),
            direction,
        )
    }
}
