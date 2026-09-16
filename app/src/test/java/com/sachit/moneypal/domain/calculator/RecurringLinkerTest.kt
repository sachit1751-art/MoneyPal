package com.sachit.moneypal.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.domain.model.RecurrentFrequency
import com.sachit.moneypal.domain.model.Transaction
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class RecurringLinkerTest {

    private val today: LocalDate = LocalDate.of(2026, 6, 15)
    private val linker = RecurringLinker(RecurringExpenseCalculator())

    private fun template(
        id: Long,
        amount: String,
        startDate: LocalDate,
        frequency: RecurrentFrequency = RecurrentFrequency.MONTHLY,
        subscriptionDay: Int? = null,
        paused: Boolean = false,
    ) = Transaction(
        id = id,
        amount = BigDecimal(amount),
        comment = "Netflix",
        date = startDate.atStartOfDay(),
        isRecurrent = true,
        recurrentFrequency = frequency,
        subscriptionDay = subscriptionDay,
        pausedAtEpochMs = if (paused) 1L else null,
    )

    private fun adHoc(
        id: Long,
        amount: String,
        date: LocalDate,
        sourceTransactionId: Long? = null,
    ) = Transaction(
        id = id,
        amount = BigDecimal(amount),
        comment = "Netflix",
        date = date.atStartOfDay(),
        sourceTransactionId = sourceTransactionId,
    )

    @Test
    fun `exact amount and date inside window links`() {
        val t = template(1L, "499", today.minusMonths(2))
        // Template bills on the 13th; ad-hoc paid on the 14th (1 day drift).
        val tx = adHoc(10L, "499", today.minusDays(1))

        assertThat(linker.link(listOf(tx), listOf(t), today)).isEqualTo(mapOf(10L to 1L))
    }

    @Test
    fun `amount differs so no link`() {
        val t = template(1L, "499", today.minusMonths(2))
        val tx = adHoc(10L, "549", today.minusDays(1))

        assertThat(linker.link(listOf(tx), listOf(t), today)).isEmpty()
    }

    @Test
    fun `paused template never links`() {
        val t = template(1L, "499", today.minusMonths(2), paused = true)
        val tx = adHoc(10L, "499", today.minusDays(1))

        assertThat(linker.link(listOf(tx), listOf(t), today)).isEmpty()
    }

    @Test
    fun `monthly clamp window links - billing on 31st matches Feb 28`() {
        // Bills on the 31st; in February the clamped occurrence is Feb 28.
        val t = template(1L, "299", LocalDate.of(2025, 12, 31))
        val tx = adHoc(10L, "299", LocalDate.of(2026, 2, 28))

        assertThat(linker.link(listOf(tx), listOf(t), today)).isEqualTo(mapOf(10L to 1L))
    }

    @Test
    fun `already linked tx with sourceTransactionId is ignored`() {
        val t = template(1L, "499", today.minusMonths(2))
        val tx = adHoc(10L, "499", today.minusDays(1), sourceTransactionId = 99L)

        assertThat(linker.link(listOf(tx), listOf(t), today)).isEmpty()
    }

    @Test
    fun `recurrent transactions are never treated as ad hoc`() {
        val t = template(1L, "499", today.minusMonths(2))
        val otherRecurrent = template(2L, "499", today.minusMonths(2))

        assertThat(linker.link(listOf(otherRecurrent), listOf(t), today)).isEmpty()
    }

    @Test
    fun `date outside drift window does not link`() {
        val t = template(1L, "499", today.minusMonths(3))
        // 10 days away from the monthly occurrence — outside ±3.
        val tx = adHoc(10L, "499", today.minusDays(10))

        assertThat(linker.link(listOf(tx), listOf(t), today)).isEmpty()
    }

    @Test
    fun `paidCyclesThisYear counts linked rows of the current year only`() {
        val t = template(1L, "499", LocalDate.of(2024, 1, 13))
        val thisYear = adHoc(10L, "499", today.minusDays(1))
        val lastYear = adHoc(11L, "499", LocalDate.of(2025, 3, 13))
        val links = linker.link(listOf(thisYear, lastYear), listOf(t), today)
        val adHocById = mapOf(thisYear.id to thisYear, lastYear.id to lastYear)

        assertThat(linker.paidCyclesThisYear(t, links, adHocById, today)).isEqualTo(1)
    }
}
