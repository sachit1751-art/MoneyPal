package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.domain.calculator.EnvelopeCalculator
import com.sachit.moneypal.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class EnvelopeAlertEvaluatorTest {

    private val evaluator = EnvelopeAlertEvaluator(BudgetThresholdEvaluator())

    private fun category(id: Long, limit: String) = Category(
        id = id,
        name = "cat$id",
        monthlyLimit = BigDecimal(limit),
    )

    private fun progress(categoryId: Long, limit: String, spent: String) =
        EnvelopeCalculator().compute(
            transactions = listOf(
                com.sachit.moneypal.domain.model.Transaction(
                    id = 1L,
                    amount = BigDecimal(spent),
                    comment = "",
                    date = null,
                    categoryId = categoryId,
                ),
            ),
            categories = listOf(category(categoryId, limit)),
        )

    @Test
    fun `no alert below 80 percent`() {
        val alerts = evaluator.evaluate(progress(1L, "100", "79")) { null }
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `alert when crossing 80 percent`() {
        val alerts = evaluator.evaluate(progress(1L, "100", "80")) { null }
        assertEquals(1, alerts.size)
        assertEquals(BudgetThreshold.EIGHTY, alerts[0].threshold)
        assertEquals(1L, alerts[0].category.id)
    }

    @Test
    fun `escalates from eighty to full`() {
        val alerts = evaluator.evaluate(progress(1L, "100", "120")) { BudgetThreshold.EIGHTY }
        assertEquals(1, alerts.size)
        assertEquals(BudgetThreshold.FULL, alerts[0].threshold)
    }

    @Test
    fun `no repeat alert at same threshold`() {
        val alerts = evaluator.evaluate(progress(1L, "100", "85")) { BudgetThreshold.EIGHTY }
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `multiple categories alert independently`() {
        val progress = progress(1L, "100", "90") + progress(2L, "50", "10")
        val alerted = mutableSetOf(2L)
        val alerts = evaluator.evaluate(progress) { if (it in alerted) BudgetThreshold.EIGHTY else null }
        assertEquals(1, alerts.size)
        assertEquals(1L, alerts[0].category.id)
    }

    @Test
    fun `overspent category alerts as full`() {
        val alerts = evaluator.evaluate(progress(1L, "100", "150")) { null }
        assertEquals(BudgetThreshold.FULL, alerts[0].threshold)
    }
}
