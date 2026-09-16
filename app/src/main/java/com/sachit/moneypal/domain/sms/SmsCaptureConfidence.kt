package com.sachit.moneypal.domain.sms

import java.math.BigDecimal

/**
 * Confidence score for an SMS capture (plan 014). Pure arithmetic, unit-tested.
 *
 * Additive signals: sender shape (25) + merchant found (30) +
 * category suggested (25) + amount sanity (20) → 0..100.
 *
 * [REVIEW_THRESHOLD] is the plan-015 review-inbox cutoff so the scorer and
 * the inbox cannot drift; keep it referenced, never re-declared.
 */
object SmsCaptureConfidence {

    /** Captures scoring below this belong in the review inbox (plan 015). */
    const val REVIEW_THRESHOLD = 60

    private val AMOUNT_CEILING = BigDecimal(1_000_000)

    fun score(
        senderLooksLikeBank: Boolean,
        merchantFound: Boolean,
        categorySuggested: Boolean,
        amount: BigDecimal,
    ): Int {
        var score = 0
        if (senderLooksLikeBank) score += 25
        if (merchantFound) score += 30
        if (categorySuggested) score += 25
        if (amount > BigDecimal.ZERO && amount <= AMOUNT_CEILING) score += 20
        return score
    }
}
