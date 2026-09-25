package com.sachit.moneypal.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.domain.model.RecurrentFrequency
import com.sachit.moneypal.domain.model.Transaction
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Plan 047: price-change shapes — no change, possible (1 differing charge),
 * confirmed (2 agreeing charges), non-recurring guard, compareTo equality.
 */
class PriceChangeDetectorTest {

    private val detector = PriceChangeDetector()

    private val template = Transaction(
        id = 1L,
        amount = BigDecimal("10.00"),
        comment = "Netflix",
        date = LocalDateTime.of(2026, 1, 15, 0, 0),
        isRecurrent = true,
        recurrentFrequency = RecurrentFrequency.MONTHLY,
    )

    private fun charge(id: Long, amount: String, day: Int, month: Int = 9) = Transaction(
        id = id,
        amount = BigDecimal(amount),
        comment = "Netflix",
        date = LocalDateTime.of(2026, month, day, 12, 0),
        sourceTransactionId = template.id,
    )

    @Test
    fun `no change when latest charge matches template`() {
        val result = detector.detect(
            template = template,
            occurrenceCharges = listOf(
                charge(2, "10.00", day = 15),
                charge(3, "10.0", day = 15, month = 8), // compareTo-equal, different scale
            ),
        )
        assertThat(result).isNull()
    }

    @Test
    fun `possible when only the latest charge differs`() {
        val result = detector.detect(
            template = template,
            occurrenceCharges = listOf(
                charge(2, "13.99", day = 15),
                charge(3, "10.00", day = 15, month = 8),
            ),
        )
        assertThat(result).isNotNull()
        assertThat(result!!.confidence).isEqualTo(PriceChangeConfidence.POSSIBLE)
        assertThat(result.newAmount.compareTo(BigDecimal("13.99"))).isEqualTo(0)
        assertThat(result.previousAmount.compareTo(BigDecimal("10.00"))).isEqualTo(0)
    }

    @Test
    fun `confirmed when last two charges agree on the new price`() {
        val result = detector.detect(
            template = template,
            occurrenceCharges = listOf(
                charge(2, "13.99", day = 15),
                charge(3, "13.99", day = 15, month = 8),
            ),
        )
        assertThat(result).isNotNull()
        assertThat(result!!.confidence).isEqualTo(PriceChangeConfidence.CONFIRMED)
        assertThat(result.changedAt).isEqualTo(java.time.LocalDate.of(2026, 9, 15))
    }

    @Test
    fun `fewer than two charges yields no detection`() {
        val result = detector.detect(
            template = template,
            occurrenceCharges = listOf(charge(2, "13.99", day = 15)),
        )
        assertThat(result).isNull()
    }

    @Test
    fun `non-recurring template is ignored`() {
        val result = detector.detect(
            template = template.copy(isRecurrent = false),
            occurrenceCharges = listOf(
                charge(2, "13.99", day = 15),
                charge(3, "13.99", day = 15, month = 8),
            ),
        )
        assertThat(result).isNull()
    }

    @Test
    fun `unlinked rows (no sourceTransactionId) are ignored`() {
        val result = detector.detect(
            template = template,
            occurrenceCharges = listOf(
                charge(2, "13.99", day = 15).copy(sourceTransactionId = null),
                charge(3, "13.99", day = 15, month = 8).copy(sourceTransactionId = null),
            ),
        )
        assertThat(result).isNull()
    }

    @Test
    fun `deleted charges are ignored`() {
        val result = detector.detect(
            template = template,
            occurrenceCharges = listOf(
                charge(2, "13.99", day = 15),
                charge(3, "13.99", day = 15, month = 8).copy(isDeleted = true),
            ),
        )
        assertThat(result).isNull()
    }

    @Test
    fun `charge at the old price after hikes resets confidence to possible`() {
        val result = detector.detect(
            template = template,
            occurrenceCharges = listOf(
                charge(2, "13.99", day = 15),
                charge(3, "10.00", day = 15, month = 8),
                charge(4, "13.99", day = 15, month = 7),
            ),
        )
        assertThat(result).isNotNull()
        assertThat(result!!.confidence).isEqualTo(PriceChangeConfidence.POSSIBLE)
    }
}
