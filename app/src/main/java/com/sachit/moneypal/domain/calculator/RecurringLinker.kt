package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.RecurrentFrequency
import com.sachit.moneypal.domain.model.Transaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Occurrence dates for [template] are matched with a ±[ALLOWED_DRIFT_DAYS] window. */
internal const val ALLOWED_DRIFT_DAYS = 3L

/** Safety cap on cycle enumeration (10+ years of weekly charges). */
private const val MAX_CYCLES = 520

/**
 * Links ad-hoc (non-recurrent) transactions to recurring templates by
 * amount + date proximity (plan 016). Pure function, unit-tested.
 *
 * A transaction links to a template when ALL hold:
 *  - template isRecurrent && !isRecurrentPaused && !isDeleted
 *  - tx.amount == template.amount (exact BigDecimal compare)
 *  - tx date is within ±[ALLOWED_DRIFT_DAYS] of the template's occurrence
 *    date for that cycle
 *  - tx is not itself recurrent and has sourceTransactionId == null
 *
 * The exact-amount match is deliberately strict; loosening to a tolerance
 * needs a user setting (price hikes would silently stop linking otherwise).
 */
class RecurringLinker @Inject constructor(
    private val calculator: RecurringExpenseCalculator,
) {

    /** Returns ad-hoc tx id → template id. An ad-hoc tx links to at most one template. */
    fun link(
        adHoc: List<Transaction>,
        templates: List<Transaction>,
        today: LocalDate,
    ): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        for (template in templates) {
            if (!template.isRecurrent || template.isRecurrentPaused || template.isDeleted) continue
            val amount = template.amount
            val occurrenceDates = occurrenceDatesThroughToday(template, today)

            for (tx in adHoc) {
                if (tx.id in result) continue // already linked to an earlier template
                if (tx.isRecurrent || tx.isDeleted) continue
                if (tx.sourceTransactionId != null) continue
                if (tx.amount != amount) continue
                val txDate = tx.date?.toLocalDate() ?: continue
                val matches = occurrenceDates.any { occurrence ->
                    ChronoUnit.DAYS.between(occurrence, txDate).let { it in -ALLOWED_DRIFT_DAYS..ALLOWED_DRIFT_DAYS }
                }
                if (matches) result[tx.id] = template.id
            }
        }
        return result
    }

    /** All occurrence dates of [template] from its start date through [today], inclusive. */
    private fun occurrenceDatesThroughToday(template: Transaction, today: LocalDate): List<LocalDate> {
        val startDate = template.date?.toLocalDate() ?: return emptyList()
        if (startDate.isAfter(today)) return emptyList()
        val endDate = template.recurrentEndDate?.toLocalDate()
        val frequency = template.recurrentFrequency ?: return emptyList()

        val dates = mutableListOf<LocalDate>()
        when (frequency) {
            RecurrentFrequency.WEEKLY -> {
                var candidate = startDate
                var cycles = 0
                while (!candidate.isAfter(today) && cycles < MAX_CYCLES) {
                    dates.add(candidate)
                    candidate = candidate.plusWeeks(1)
                    cycles++
                }
            }

            RecurrentFrequency.BIWEEKLY -> {
                var candidate = startDate
                var cycles = 0
                while (!candidate.isAfter(today) && cycles < MAX_CYCLES) {
                    dates.add(candidate)
                    candidate = candidate.plusWeeks(2)
                    cycles++
                }
            }

            RecurrentFrequency.MONTHLY -> {
                val billingDay = template.subscriptionDay ?: startDate.dayOfMonth
                var month = YearMonth.from(startDate)
                var candidate = startDate
                var cycles = 0
                while (!candidate.isAfter(today) && cycles < MAX_CYCLES) {
                    dates.add(candidate)
                    month = month.plusMonths(1)
                    candidate = month.atDay(billingDay.coerceIn(1, month.lengthOfMonth()))
                    cycles++
                }
            }
        }
        if (endDate != null) return dates.filter { !it.isAfter(endDate) }
        return dates
    }

    /** Number of [template]'s cycles paid ad-hoc this calendar year, from the link map. */
    fun paidCyclesThisYear(
        template: Transaction,
        links: Map<Long, Long>,
        adHocById: Map<Long, Transaction>,
        today: LocalDate,
    ): Int = links.count { (adHocId, templateId) ->
        templateId == template.id && adHocById[adHocId]?.let { tx ->
            tx.date?.toLocalDate()?.year == today.year
        } ?: false
    }
}
