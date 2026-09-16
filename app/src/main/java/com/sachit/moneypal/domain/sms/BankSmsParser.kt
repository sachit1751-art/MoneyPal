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
 *    "sent", "transferred", charged, autopay, EMI, billed, payment (plan 013)
 *  - credit keywords: credited, received, refund, deposited, cashback, "added"
 *  - OTPs, promotions and balance-only messages produce NO match (no keyword +
 *    amount pair).
 *
 * Amount selection: real bank SMS put the amount *before* the keyword
 * ("Rs 1,234.56 debited from a/c XX99") or *after* it ("debited for Rs 500"),
 * so the whole body is scanned and the currency-qualified amount **closest to
 * the keyword occurrence** wins. Amounts preceded by a balance/limit cue
 * ("Bal: Rs 5,000", "Avl lmt Rs 1,00,000") are noise and never candidates
 * (plan 013). Amounts without a currency cue (account numbers, reference IDs)
 * are never candidates.
 *
 * Direction disambiguation (plan 013): when BOTH a debit and a credit keyword
 * appear (e.g. "Payment of Rs 999 received on card"), the keyword nearest its
 * best amount wins; a tie means the message is ambiguous and rejected.
 *
 * Connector fallback (plan 013): "Txn of Rs 1,234" / "order of Rs 250" have no
 * debit verb at all, but a transaction connector + currency amount. Such
 * messages are universally spends, so they parse as debits.
 */
object BankSmsParser {

    // Comma-grouped form first (requiring at least one group) so plain 4+ digit
    // numbers like "1000" fall through to \d+ instead of truncating to "100".
    private const val NUMBER = """(?:\d{1,3}(?:,\d{2,3})+|\d+)(?:\.\d{1,4})?"""

    /** amount with a currency cue before or after: Rs/Rs./INR/₹/$/€/£/Rs-suffix forms */
    private val CURRENCY_AMOUNT = Regex(
        pattern =
            "(?:(?:Rs\\.?|INR|₹|\\$|€|£)\\s?($NUMBER)|($NUMBER)\\s?(?:Rs\\.?|INR|₹))",
        RegexOption.IGNORE_CASE,
    )

    private val DEBIT_KEYWORD = Regex(
        "(?:\\b(?:debited|debit|debits|spent|spend|paid|withdrawn|withdraw|deducted|deduct|" +
            "purchased|purchase|sent|transferred|transfer|charged|charge|autopay|auto_pay|" +
            "emi|billed|billing|payment)\\b)",
        RegexOption.IGNORE_CASE,
    )

    private val CREDIT_KEYWORD = Regex(
        "(?:\\b(?:credited|credit|received|recd|recv|refund|refunded|deposited|deposit|cashback|cash_back|added)\\b)",
        RegexOption.IGNORE_CASE,
    )

    private val OTP_NOISE = Regex(
        "(?i)\\b(?:otp|one\\s?time\\s?password|password|verification\\s?code)\\b"
    )

    /** Amounts immediately preceded by a balance/limit cue are noise, not the txn amount. */
    private val BALANCE_CUE = Regex(
        "(?i)(?:bal|balance|avl\\s?bal|available|limit|lmt|lmit|total\\s?bal)[.:\\s]*$"
    )

    /** Second-chance pattern: "Txn of Rs 1,234" / "order for Rs 250" with no debit verb. */
    private val CONNECTOR_AMOUNT = Regex(
        "(?i)(?:txn|transaction|order|payment)\\s+(?:of|for|:)?\\s*(?:Rs\\.?|INR|₹|\\$|€|£)\\s?($NUMBER)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Heuristic used by smart-capture (plan 014): does this sender look like a
     * bank/PSP sender? Indian-style ID senders (`JD-HDFC`, `VM-XXXPAY`),
     * short codes (`404040`) and numeric senders with country code all count.
     */
    fun isLikelyBankSender(sender: String): Boolean {
        val s = sender.trim()
        if (s.isEmpty()) return false
        if (s.contains('-') && s.any { it.isLetter() }) return true
        return s.all { it.isDigit() } || (s.startsWith("+") && s.drop(1).all { it.isDigit() })
    }

    /** Parse [body] from [sender]. Returns null when the message is not a money SMS. */
    fun parse(sender: String, body: String, timestampMillis: Long): BankSmsMatch? {
        if (body.isBlank()) return null

        // OTPs and verification messages are never transactions, even if an amount
        // appears ("OTP 123456 to debit Rs 500") — bail out before keyword matching.
        if (OTP_NOISE.containsMatchIn(body)) return null

        val debitHit = DEBIT_KEYWORD.find(body)
        val creditHit = CREDIT_KEYWORD.find(body)

        val keywordRange: IntRange
        val isCredit: Boolean
        when {
            debitHit == null && creditHit == null -> {
                // Connector fallback (plan 013): "Txn of Rs 1,234" has no verb.
                val connector = CONNECTOR_AMOUNT.find(body) ?: return null
                val value = parseAmount(connector.groupValues[1]) ?: return null
                if (value <= BigDecimal.ZERO) return null
                return BankSmsMatch(
                    amount = value,
                    isCredit = false,
                    sender = sender.trim(),
                    timestampMillis = timestampMillis,
                    rawAmountText = connector.groupValues[1],
                    body = body,
                )
            }

            creditHit == null -> {
                keywordRange = debitHit!!.range
                isCredit = false
            }

            debitHit == null -> {
                keywordRange = creditHit!!.range
                isCredit = true
            }

            else -> {
                // Both directions present (plan 013): the keyword nearest its best
                // amount wins; a tie (or neither has an amount) is ambiguous.
                val debitBest = closestAmountTo(body, debitHit.range)
                val creditBest = closestAmountTo(body, creditHit.range)
                val useDebit = when {
                    debitBest == null && creditBest == null -> return null
                    creditBest == null -> true
                    debitBest == null -> false
                    debitBest.first < creditBest.first -> true
                    debitBest.first > creditBest.first -> false
                    else -> return null // tie → ambiguous, reject
                }
                keywordRange = (if (useDebit) debitHit else creditHit).range
                isCredit = !useDebit
            }
        }

        val amountText = closestAmountTo(body, keywordRange)?.second
            ?: CONNECTOR_AMOUNT.find(body)?.groupValues?.getOrNull(1)
            ?: return null

        val value = parseAmount(amountText) ?: return null
        if (value <= BigDecimal.ZERO) return null

        return BankSmsMatch(
            amount = value,
            isCredit = isCredit,
            sender = sender.trim(),
            timestampMillis = timestampMillis,
            rawAmountText = amountText,
            body = body,
        )
    }

    /**
     * Scan the whole [body] for currency-qualified amounts and return the one
     * closest to the keyword occurrence at [keywordRange], as (distance, text).
     * Handles both "Rs 500 debited" (amount before) and "debited Rs 500"
     * (amount after). Balance/limit amounts are skipped (plan 013).
     */
    private fun closestAmountTo(body: String, keywordRange: IntRange): Pair<Int, String>? {
        return CURRENCY_AMOUNT.findAll(body)
            .mapNotNull { match ->
                val text = match.groupValues[1].ifBlank { match.groupValues[2] }
                if (text.isBlank()) return@mapNotNull null
                // Balance/limit amounts ("Bal: Rs 5,000", "Avl lmt Rs 1,00,000")
                // are noise, never the transaction amount (plan 013).
                val prefix = body.substring(0, match.range.first).takeLast(24)
                if (BALANCE_CUE.containsMatchIn(prefix)) return@mapNotNull null
                val distance = if (match.range.first > keywordRange.last) {
                    match.range.first - keywordRange.last // amount after keyword
                } else {
                    keywordRange.first - match.range.last // amount before keyword
                }
                distance to text
            }
            .minByOrNull { it.first }
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
