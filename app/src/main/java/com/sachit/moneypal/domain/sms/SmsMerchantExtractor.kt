package com.sachit.moneypal.domain.sms

/**
 * Extracts the most merchant-like proper noun from a bank SMS body (plan 014).
 * Pure Kotlin, unit-tested. Returns null when nothing confident is found.
 *
 * Heuristics, in priority order:
 *  1. "at <MERCHANT>", "to <MERCHANT>", "on <MERCHANT>" appearing after the
 *     currency amount (e.g. "debited for Rs 500 at AMAZON").
 *  2. Known-merchant dictionary hit (small curated seed list; unknown
 *     merchants come from rule 1).
 *  3. null.
 *
 * The result is title-cased ("AMAZON PAY" -> "Amazon Pay") and never a bank
 * name, card fragment or account reference.
 */
object SmsMerchantExtractor {

    private val AMOUNT_ANCHOR = Regex("(?i)(?:Rs\\.?|INR|₹|\\$|€|£)\\s?\\d")

    private val PREPOSITION = Regex("(?i)\\b(?:at|to|on)\\s+")

    /** A merchant word: starts with a letter; letters/digits/&/apostrophe/dot/hyphen inside. */
    private val WORD = Regex("^[A-Za-z][A-Za-z0-9&.'\\-]*$")

    /** Card/account fragments like "XX1234" or "XX" — never merchant names. */
    private val CARD_FRAGMENT = Regex("(?i)^(?:x{2,}\\d*|\\d+)$")

    /** Words that terminate merchant capture or disqualify the candidate. */
    private val NOISE_TOKENS = setOf(
        "bank", "hdfc", "icici", "sbi", "axis", "kotak", "pnb", "idfc",
        "indusind", "bandhan", "yesbank", "federal", "paytm", "phonepe",
        "gpay", "googlepay", "upi", "imps", "neft", "rtgs", "ach", "nach",
        "autopay", "card", "credit", "debit", "account", "acct", "wallet",
        "via", "using", "on", "in", "at", "to", "dated", "date", "ref",
        "reference", "no", "number", "and", "the", "a",
        "order", "id", "subscription", "mandate", "txn", "transaction",
    )

    private val KNOWN_MERCHANTS = listOf(
        "AMAZON", "SWIGGY", "ZOMATO", "UBER", "OLA", "NETFLIX", "SPOTIFY",
        "JIO", "AIRTEL", "BLINKIT", "BIGBASKET", "DMART", "FLIPKART",
        "MYNTRA", "AJIO", "IRCTC", "INDIGO", "RAPIDO", "DOMINOS",
        "MCDONALD", "STARBUCKS", "KFC", "HOTSTAR", "YOUTUBE",
    )

    fun extract(body: String): String? {
        if (body.isBlank()) return null

        // Rule 1: preposition candidates strictly after the currency amount.
        val anchor = AMOUNT_ANCHOR.find(body)?.range?.last ?: -1
        PREPOSITION.findAll(body)
            .filter { it.range.first > anchor }
            .forEach { preposition ->
                extractAfter(body, preposition.range.last + 1)?.let { return it }
            }

        // Rule 2: curated dictionary.
        if (anchor >= 0) {
            KNOWN_MERCHANTS.firstOrNull { body.contains(it, ignoreCase = true) }?.let {
                return titleCase(it)
            }
        }
        return null
    }

    /** Reads up to 3 merchant words from [start]; null when the candidate is noise. */
    private fun extractAfter(body: String, start: Int): String? {
        val words = body.substring(start).trim().split(Regex("\\s+"))
        val picked = mutableListOf<String>()
        for (raw in words) {
            val word = raw.trim(',', '.', ':', ';', '!', '(', ')', '-')
            if (word.isEmpty()) continue
            if (!WORD.matches(word) || CARD_FRAGMENT.matches(word)) break
            if (word.lowercase() in NOISE_TOKENS) break
            picked.add(word)
            if (picked.size == 3) break
        }
        if (picked.isEmpty()) return null
        // Bank SMS shout merchant names ("at AMAZON", "at Amazon Pay"); a
        // candidate with no uppercase at all ("to save 20") is a verb fragment,
        // not a merchant — reject and let the dictionary rule try.
        if (picked.none { word -> word.any { it.isUpperCase() } }) return null
        val merchant = picked.joinToString(" ")
        // Reject anything containing a bank/PSP token as a whole word
        // ("HDFC Pay"); substring matching would wrongly kill "Amazon Pay"
        // ("amaz-ON").
        if (merchant.split(' ').any { it.lowercase() in NOISE_TOKENS }) return null
        return titleCase(merchant)
    }

    private fun titleCase(s: String): String = s.split(' ').joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}
