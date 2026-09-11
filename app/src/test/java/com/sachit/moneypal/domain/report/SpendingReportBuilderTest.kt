package com.sachit.moneypal.domain.report

import com.sachit.moneypal.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class SpendingReportBuilderTest {

    private val builder = SpendingReportBuilder()

    private fun tx(
        amount: String,
        day: Int,
        comment: String = "",
        hour: Int = 12,
    ) = Transaction(
        amount = BigDecimal(amount),
        comment = comment,
        date = LocalDateTime.of(2026, 9, day, hour, 0),
    )

    @Test
    fun `daily report only includes that day`() {
        val report = builder.build(
            scope = ReportScope.DAILY,
            transactions = listOf(
                tx("10.00", 10, "Coffee"),
                tx("20.00", 10, "Lunch"),
                tx("99.00", 11, "Other day"),
            ),
            date = LocalDate_of(9, 10),
            currencySymbol = "$",
        )
        assertEquals(BigDecimal("30.00"), report.totalSpent)
        assertEquals(2, report.entryCount)
    }

    @Test
    fun `monthly report aggregates whole month and ranks categories`() {
        val report = builder.build(
            scope = ReportScope.MONTHLY,
            transactions = listOf(
                tx("50.00", 1, "Groceries"),
                tx("30.00", 5, "Groceries"),
                tx("100.00", 8, "Electronics"),
                tx("5.00", 20, "Coffee"),
                tx("40.00", 10, ""),
            ),
            date = LocalDate_of(9, 21),
            currencySymbol = "$",
        )
        assertEquals(BigDecimal("225.00"), report.totalSpent)
        assertEquals(5, report.entryCount)
        assertEquals(3, report.topCategories.size)
        assertEquals("Electronics", report.topCategories[0].first)
        assertEquals(BigDecimal("100.00"), report.topCategories[0].second)
        assertEquals("Groceries", report.topCategories[1].first)
        assertEquals(BigDecimal("80.00"), report.topCategories[1].second)
    }

    @Test
    fun `income is reported separately and not counted as spend`() {
        val report = builder.build(
            scope = ReportScope.MONTHLY,
            transactions = listOf(
                tx("1000.00", 2, ""),
                tx("-500.00", 3, "Salary"), // negative = income in MoneyPal
            ),
            date = LocalDate_of(9, 5),
            currencySymbol = "$",
        )
        assertEquals(BigDecimal("1000.00"), report.totalSpent)
        assertEquals(BigDecimal("500.00"), report.totalIncome)
    }

    @Test
    fun `over daily budget flagged`() {
        val report = builder.build(
            scope = ReportScope.DAILY,
            transactions = listOf(tx("150.00", 10)),
            date = LocalDate_of(9, 10),
            currencySymbol = "$",
            dailyBudget = BigDecimal("100.00"),
        )
        assertTrue(report.isOverDailyBudget)
    }

    @Test
    fun `empty scope yields zeroed report without crash`() {
        val report = builder.build(
            scope = ReportScope.DAILY,
            transactions = emptyList(),
            date = LocalDate_of(9, 10),
            currencySymbol = "$",
        )
        assertEquals(BigDecimal.ZERO, report.totalSpent)
        assertEquals(0, report.entryCount)
        assertNull(report.biggestExpense)
    }

    @Test
    fun `share text contains key lines`() {
        val report = builder.build(
            scope = ReportScope.DAILY,
            transactions = listOf(tx("12.50", 10, "Coffee")),
            date = LocalDate_of(9, 10),
            currencySymbol = "$",
            dailyBudget = BigDecimal("20.00"),
        )
        val text = builder.toShareText(report, appName = "MoneyPal")
        assertTrue(text.contains("Daily report"))
        assertTrue(text.contains("$12.50"))
        assertTrue(text.contains("✅ within"))
        assertTrue(text.contains("• Coffee: $12.50"))
        assertTrue(text.contains("via MoneyPal"))
    }

    private fun LocalDate_of(month: Int, day: Int): java.time.LocalDate =
        java.time.LocalDate.of(2026, month, day)
}
