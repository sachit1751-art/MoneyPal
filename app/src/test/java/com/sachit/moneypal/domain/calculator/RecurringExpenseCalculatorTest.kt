package com.sachit.moneypal.domain.calculator

import com.sachit.moneypal.domain.model.PaidRecurrentOccurrence
import com.sachit.moneypal.domain.model.RecurrentFrequency
import com.sachit.moneypal.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class RecurringExpenseCalculatorTest {

    private val calculator = RecurringExpenseCalculator()

    @Test
    fun weeklyRecurring_dueOnSeventhDay_returnsTrue() {
        val start = LocalDate.of(2026, 3, 1)
        val tx = recurrentTransaction(start, RecurrentFrequency.WEEKLY)

        assertTrue(calculator.isRecurringDueToday(tx, start.plusDays(7)))
        assertFalse(calculator.isRecurringDueToday(tx, start.plusDays(6)))
    }

    @Test
    fun monthlyRecurring_usesSubscriptionDay() {
        val start = LocalDate.of(2026, 1, 2)
        val tx = recurrentTransaction(start, RecurrentFrequency.MONTHLY, subscriptionDay = 10)

        assertTrue(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 2, 10)))
        assertFalse(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 2, 9)))
    }

    @Test
    fun monthlyRecurring_billingOnThe31stIsClampedToTheLastDayOfShortMonths() {
        val start = LocalDate.of(2026, 1, 31)
        val tx = recurrentTransaction(start, RecurrentFrequency.MONTHLY, subscriptionDay = 31)

        // 31st of a 30-day month -> the 30th
        assertTrue(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 4, 30)))
        assertFalse(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 4, 29)))
        // 31st of a 28-day February (2026 is not a leap year) -> the 28th
        assertTrue(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 2, 28)))
        assertFalse(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 2, 27)))
        // Months that have the 31st keep it
        assertTrue(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 5, 31)))
        assertFalse(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 5, 30)))
    }

    @Test
    fun monthlyRecurring_billingOnThe30thIsClampedToTheLastDayOfFebruary() {
        val start = LocalDate.of(2026, 1, 30)
        val tx = recurrentTransaction(start, RecurrentFrequency.MONTHLY, subscriptionDay = 30)

        // 2026 is not a leap year
        assertTrue(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 2, 28)))
        assertFalse(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 2, 27)))
        val leapTx = recurrentTransaction(
            startDate = LocalDate.of(2028, 1, 30),
            frequency = RecurrentFrequency.MONTHLY,
            subscriptionDay = 30,
            recurrentEndDate = LocalDateTime.of(2029, 1, 1, 0, 0),
        )
        // Leap year February keeps the 29th
        assertTrue(calculator.isRecurringDueToday(leapTx, LocalDate.of(2028, 2, 29)))
        assertFalse(calculator.isRecurringDueToday(leapTx, LocalDate.of(2028, 2, 28)))
    }

    @Test
    fun monthlyRecurring_billingOnThe29thIsClampedInANonLeapFebruary() {
        val start = LocalDate.of(2026, 1, 29)
        val tx = recurrentTransaction(start, RecurrentFrequency.MONTHLY, subscriptionDay = 29)

        assertTrue(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 2, 28)))
        assertFalse(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 2, 27)))
        assertTrue(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 3, 29)))
    }

    @Test
    fun monthlyRecurring_defaultBillingDayFallsBackToTheStartDatesDay() {
        val start = LocalDate.of(2026, 1, 15)
        val tx = recurrentTransaction(start, RecurrentFrequency.MONTHLY, subscriptionDay = null)

        assertTrue(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 2, 15)))
        assertFalse(calculator.isRecurringDueToday(tx, LocalDate.of(2026, 2, 14)))
    }

    @Test
    fun monthlyOccurrenceDay_clampsToTheTargetMonthsLength() {
        assertEquals(28, monthlyOccurrenceDay(31, java.time.YearMonth.of(2026, 2)))
        assertEquals(29, monthlyOccurrenceDay(31, java.time.YearMonth.of(2028, 2)))
        assertEquals(30, monthlyOccurrenceDay(31, java.time.YearMonth.of(2026, 4)))
        assertEquals(31, monthlyOccurrenceDay(31, java.time.YearMonth.of(2026, 5)))
        assertEquals(15, monthlyOccurrenceDay(15, java.time.YearMonth.of(2026, 2)))
    }

    @Test
    fun calculateRecurringDueToday_sumsOnlyDueRecurringTransactions() {
        val today = LocalDate.of(2026, 3, 15)
        val dueWeekly = recurrentTransaction(today.minusDays(14), RecurrentFrequency.WEEKLY, amount = BigDecimal("5.00"))
        val dueMonthly = recurrentTransaction(today.minusMonths(1), RecurrentFrequency.MONTHLY, subscriptionDay = 15, amount = BigDecimal("7.50"))
        val notDue = recurrentTransaction(today.minusDays(1), RecurrentFrequency.WEEKLY, amount = BigDecimal("20.00"))

        val result = calculator.calculateRecurringDueToday(listOf(dueWeekly, dueMonthly, notDue), today)

        assertEquals(BigDecimal("12.50"), result)
    }

    @Test
    fun calculateRecurringDueToday_excludesAnOccurrenceAlreadyMarkedPaidForToday() {
        val today = LocalDate.of(2026, 3, 15)
        val dueToday = recurrentTransaction(
            LocalDate.of(2026, 3, 1), RecurrentFrequency.MONTHLY, subscriptionDay = 15, amount = BigDecimal("15.00")
        ).let { it.copy(id = 7L) }

        val result = calculator.calculateRecurringDueToday(
            transactions = listOf(dueToday),
            today = today,
            paidOccurrences = setOf(PaidRecurrentOccurrence(transactionId = 7L, occurrenceDate = today)),
        )

        assertEquals(BigDecimal.ZERO, result)
    }

    @Test
    fun calculateRecurringDueToday_stillCountsAnUnpaidOccurrence() {
        val today = LocalDate.of(2026, 3, 15)
        val dueToday = recurrentTransaction(
            LocalDate.of(2026, 3, 1), RecurrentFrequency.MONTHLY, subscriptionDay = 15, amount = BigDecimal("15.00")
        ).let { it.copy(id = 7L) }

        val result = calculator.calculateRecurringDueToday(
            transactions = listOf(dueToday),
            today = today,
            paidOccurrences = emptySet(),
        )

        assertEquals(BigDecimal("15.00"), result)
    }

    @Test
    fun calculateRecurringDueToday_apaidOccurrenceOnADifferentDateDoesNotSuppressTodaysDue() {
        val today = LocalDate.of(2026, 3, 15)
        val dueToday = recurrentTransaction(
            LocalDate.of(2026, 3, 1), RecurrentFrequency.MONTHLY, subscriptionDay = 15, amount = BigDecimal("15.00")
        ).let { it.copy(id = 7L) }

        val result = calculator.calculateRecurringDueToday(
            transactions = listOf(dueToday),
            today = today,
            paidOccurrences = setOf(PaidRecurrentOccurrence(transactionId = 7L, occurrenceDate = LocalDate.of(2026, 2, 15))),
        )

        assertEquals(BigDecimal("15.00"), result)
    }

    private fun recurrentTransaction(
        startDate: LocalDate,
        frequency: RecurrentFrequency,
        subscriptionDay: Int? = null,
        amount: BigDecimal = BigDecimal("10.00"),
        recurrentEndDate: LocalDateTime? = LocalDateTime.of(2027, 1, 1, 0, 0),
    ): Transaction = Transaction.create(
        amount = amount,
        comment = "",
        date = startDate.atStartOfDay(),
        isRecurrent = true,
        recurrentFrequency = frequency,
        recurrentEndDate = recurrentEndDate,
        subscriptionDay = subscriptionDay
    )
}
