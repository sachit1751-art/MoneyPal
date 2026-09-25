package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.Transaction
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/**
 * Detection confidence for a subscription price change (plan 047).
 */
enum class PriceChangeConfidence {
    /** One charge already differs from the template amount. */
    POSSIBLE,

    /** The last two charges agree with each other but not with the template. */
    CONFIRMED,
}

/**
 * A detected price change on a recurring template.
 */
data class PriceChange(
    val previousAmount: BigDecimal,
    val newAmount: BigDecimal,
    /** Date of the most recent charge at the new price. */
    val changedAt: LocalDate,
    val confidence: PriceChangeConfidence,
)

/**
 * Pure detector for subscription price changes (plan 047): compares a
 * recurring template's amount against its **settled occurrence charges**
 * (the per-occurrence transaction rows carrying [Transaction.sourceTransactionId]
 * = template id). Two consecutive charges at a different amount = confirmed;
 * one differing charge = possible. Money equality always uses [BigDecimal.compareTo]
 * (plan-024 lesson: never compare money by string/float).
 */
class PriceChangeDetector @Inject constructor() {

    /**
     * @param template the recurring template row (`isRecurrent = true`).
     * @param occurrenceCharges occurrence rows linked to the template; any
     *        order; must exclude deleted rows.
     */
    fun detect(
        template: Transaction,
        occurrenceCharges: List<Transaction>,
    ): PriceChange? {
        if (!template.isRecurrent) return null

        val charges = occurrenceCharges
            .filter { !it.isDeleted && it.sourceTransactionId != null }
            .sortedByDescending { it.date }
        if (charges.size < 2) return null

        val latest = charges[0]
        val previous = charges[1]

        // Template already matches the newest charge — nothing to report.
        if (latest.amount.compareTo(template.amount) == 0) return null

        val confidence = when {
            previous.amount.compareTo(latest.amount) == 0 -> PriceChangeConfidence.CONFIRMED
            else -> PriceChangeConfidence.POSSIBLE
        }

        // Only surface a change when the direction is stable: the latest
        // charge differs from the template. The previous charge determines
        // the "previous amount" shown to the user.
        return PriceChange(
            previousAmount = template.amount,
            newAmount = latest.amount,
            changedAt = latest.date?.toLocalDate() ?: return null,
            confidence = confidence,
        )
    }
}
