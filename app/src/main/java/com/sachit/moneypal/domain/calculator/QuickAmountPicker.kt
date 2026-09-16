package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.Transaction
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Picks the user's most-frequent exact amounts for the numpad quick-amount
 * chips (plan 021). Pure function, unit-tested; follows the CategorySuggester
 * pattern for calculators.
 *
 * Ranking: usage count desc, ties broken by most-recent use. Only amounts
 * used >= [MIN_USES] times qualify. Credits, adjustments and recurring
 * transactions are excluded (recurring amounts are auto-entered anyway).
 * Amounts are compared with trailing zeros stripped so 50.00 and 50 merge.
 */
class QuickAmountPicker @Inject constructor() {

    companion object {
        /** Below this many uses an amount is noise, not a habit. */
        const val MIN_USES = 3

        /** Chip-row capacity. */
        const val MAX_AMOUNTS = 4
    }

    fun pick(
        history: List<Transaction>,
        minUses: Int = MIN_USES,
        max: Int = MAX_AMOUNTS,
    ): List<BigDecimal> {
        if (history.isEmpty()) return emptyList()

        data class Stat(var count: Int = 0, var latest: Long = Long.MIN_VALUE)

        // Group by the normalized plain-string form: stripTrailingZeros() alone
        // yields 5E+1 for 50, whose equals()/compareTo() semantics make map
        // keys unreliable. Plain strings merge 50.00/50/5E+1 correctly.
        val stats = mutableMapOf<String, Pair<BigDecimal, Stat>>()
        for (tx in history) {
            if (tx.isDeleted || tx.isCredit || tx.isAdjustment || tx.isRecurrent || tx.isIncome) continue
            val amount = tx.amount ?: continue
            if (amount.signum() <= 0) continue
            val key = amount.stripTrailingZeros().toPlainString()
            // Canonical representative from the key so returned values are
            // scale-normalized (50.00 and 50 both yield BigDecimal("50")).
            val stat = stats.getOrPut(key) { BigDecimal(key) to Stat() }
            stat.second.count++
            stat.second.latest = maxOf(stat.second.latest, tx.createdAt)
        }

        return stats.filterValues { it.second.count >= minUses }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Pair<BigDecimal, Stat>>> { it.value.second.count }
                    .thenByDescending { it.value.second.latest }
                    .thenBy { it.key },
            )
            .take(max)
            .map { it.value.first }
    }
}
