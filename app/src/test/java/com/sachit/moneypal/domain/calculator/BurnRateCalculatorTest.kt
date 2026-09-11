package com.sachit.moneypal.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BurnRateCalculatorTest {

    private val calculator = BurnRateCalculator()

    @Test
    fun `on pace when spend matches elapsed time`() {
        val projection = calculator.calculate(
            totalBudget = BigDecimal(3000),
            spentInPeriod = BigDecimal(1000),
            periodStart = LocalDate.of(2026, 9, 1),
            periodEnd = LocalDate.of(2026, 9, 30),
            today = LocalDate.of(2026, 9, 10), // day 10 of 30 → 1/3 elapsed
        )
        assertEquals(100, projection.pacePercent)
        assertEquals(0, BigDecimal.ZERO.compareTo(projection.projectedOverspend))
        assertNull(projection.projectedExhaustionDate)
    }

    @Test
    fun `over pace reports percent faster and exhaustion date`() {
        // 20 days in a 20-day period, but all money spent by day 10 → pace 200%
        val projection = calculator.calculate(
            totalBudget = BigDecimal(2000),
            spentInPeriod = BigDecimal(1000),
            periodStart = LocalDate.of(2026, 9, 1),
            periodEnd = LocalDate.of(2026, 9, 20),
            today = LocalDate.of(2026, 9, 10), // half elapsed, half spent → on pace
        )
        assertEquals(100, projection.pacePercent)

        val fast = calculator.calculate(
            totalBudget = BigDecimal(2000),
            spentInPeriod = BigDecimal(1500),
            periodStart = LocalDate.of(2026, 9, 1),
            periodEnd = LocalDate.of(2026, 9, 20),
            today = LocalDate.of(2026, 9, 10), // 75% spent in 50% of time → 150%
        )
        assertEquals(150, fast.pacePercent)
        assertTrue(fast.projectedOverspend > BigDecimal.ZERO)
        // daily burn 150 → 2000/150 ≈ 13 days → Sep 13
        assertEquals(LocalDate.of(2026, 9, 13), fast.projectedExhaustionDate)
    }

    @Test
    fun `under pace reports slower percent and no exhaustion`() {
        val projection = calculator.calculate(
            totalBudget = BigDecimal(2000),
            spentInPeriod = BigDecimal(500),
            periodStart = LocalDate.of(2026, 9, 1),
            periodEnd = LocalDate.of(2026, 9, 20),
            today = LocalDate.of(2026, 9, 10), // 25% spent in 50% of time → 50%
        )
        assertEquals(50, projection.pacePercent)
        assertEquals(0, BigDecimal.ZERO.compareTo(projection.projectedOverspend))
        assertNull(projection.projectedExhaustionDate)
    }

    @Test
    fun `no spending yields zero pace and no projection`() {
        val projection = calculator.calculate(
            totalBudget = BigDecimal(1000),
            spentInPeriod = BigDecimal.ZERO,
            periodStart = LocalDate.of(2026, 9, 1),
            periodEnd = LocalDate.of(2026, 9, 30),
            today = LocalDate.of(2026, 9, 15),
        )
        assertEquals(0, projection.pacePercent)
        assertNull(projection.projectedExhaustionDate)
    }

    @Test
    fun `zero budget yields zero pace`() {
        val projection = calculator.calculate(
            totalBudget = BigDecimal.ZERO,
            spentInPeriod = BigDecimal(100),
            periodStart = LocalDate.of(2026, 9, 1),
            periodEnd = LocalDate.of(2026, 9, 30),
            today = LocalDate.of(2026, 9, 15),
        )
        assertEquals(0, projection.pacePercent)
        assertNull(projection.projectedExhaustionDate)
    }

    @Test
    fun `today clamped into period bounds`() {
        // Period finished; projecting with a late "today" must not crash or go negative.
        val projection = calculator.calculate(
            totalBudget = BigDecimal(1000),
            spentInPeriod = BigDecimal(800),
            periodStart = LocalDate.of(2026, 9, 1),
            periodEnd = LocalDate.of(2026, 9, 10),
            today = LocalDate.of(2026, 10, 1),
        )
        assertEquals(100, projection.pacePercent)
    }
}
