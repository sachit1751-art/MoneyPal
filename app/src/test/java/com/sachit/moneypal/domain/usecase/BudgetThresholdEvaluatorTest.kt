package com.sachit.moneypal.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetThresholdEvaluatorTest {

    private val evaluator = BudgetThresholdEvaluator()

    @Test
    fun `no alert below 80 percent`() {
        assertNull(evaluator.evaluate(spent = 79.0, budget = 100.0, lastAlertedThreshold = null))
    }

    @Test
    fun `alert at exactly 80 percent`() {
        assertEquals(BudgetThreshold.EIGHTY, evaluator.evaluate(80.0, 100.0, null))
    }

    @Test
    fun `alert at 100 percent and above`() {
        assertEquals(BudgetThreshold.FULL, evaluator.evaluate(100.0, 100.0, null))
        assertEquals(BudgetThreshold.FULL, evaluator.evaluate(150.0, 100.0, null))
    }

    @Test
    fun `no repeat alert at same threshold`() {
        assertEquals(
            null,
            evaluator.evaluate(85.0, 100.0, BudgetThreshold.EIGHTY),
        )
    }

    @Test
    fun `escalates from eighty to full`() {
        assertEquals(
            BudgetThreshold.FULL,
            evaluator.evaluate(100.0, 100.0, BudgetThreshold.EIGHTY),
        )
    }

    @Test
    fun `no alert when budget is zero or negative`() {
        assertNull(evaluator.evaluate(50.0, 0.0, null))
        assertNull(evaluator.evaluate(50.0, -10.0, null))
    }

    @Test
    fun `percent consumed rounds to nearest int`() {
        assertEquals(0, evaluator.percentConsumed(0.0, 100.0))
        assertEquals(45, evaluator.percentConsumed(45.4, 100.0))
        assertEquals(46, evaluator.percentConsumed(45.5, 100.0))
        assertEquals(0, evaluator.percentConsumed(10.0, 0.0))
    }
}
