package com.sachit.moneypal.domain.datahealth

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DataHealthScannerTest {

    private val scanner = DataHealthScanner()

    private fun input(
        total: Int = 0,
        income: Int = 0,
        sms: Int = 0,
        confidences: List<Int> = emptyList(),
        attachmentPaths: List<String> = emptyList(),
        existingPaths: Set<String> = setOf(),
        duplicates: Int = 0,
        oldest: Long? = null,
    ) = DataHealthScanInput(
        totalTransactions = total,
        incomeCount = income,
        smsCapturedCount = sms,
        smsConfidences = confidences,
        attachmentPaths = attachmentPaths,
        existingAttachmentPaths = existingPaths,
        duplicateClientGeneratedIdCount = duplicates,
        oldestDateMillis = oldest,
    )

    @Test
    fun `empty scan produces zeroed report`() {
        val report = scanner.scan(input())

        assertThat(report.totalTransactions).isEqualTo(0)
        assertThat(report.incomeCount).isEqualTo(0)
        assertThat(report.expenseCount).isEqualTo(0)
        assertThat(report.smsCapturedCount).isEqualTo(0)
        assertThat(report.medianSmsConfidence).isNull()
        assertThat(report.attachmentCount).isEqualTo(0)
        assertThat(report.missingAttachmentCount).isEqualTo(0)
        assertThat(report.duplicateClientGeneratedIdCount).isEqualTo(0)
        assertThat(report.oldestTransactionDate).isNull()
        assertThat(report.hasIssues).isFalse()
    }

    @Test
    fun `expense count is total minus income`() {
        val report = scanner.scan(input(total = 10, income = 3))

        assertThat(report.expenseCount).isEqualTo(7)
    }

    @Test
    fun `defensive clamp when income exceeds total`() {
        val report = scanner.scan(input(total = 2, income = 5))

        assertThat(report.expenseCount).isEqualTo(0)
    }

    @Test
    fun `missing attachment detected when file path is absent`() {
        val report = scanner.scan(
            input(
                attachmentPaths = listOf("/data/receipts/a.jpg", "/data/receipts/b.jpg"),
                existingPaths = setOf("/data/receipts/a.jpg"),
            )
        )

        assertThat(report.attachmentCount).isEqualTo(2)
        assertThat(report.missingAttachmentCount).isEqualTo(1)
        assertThat(report.hasIssues).isTrue()
    }

    @Test
    fun `all attachments present means no issue`() {
        val report = scanner.scan(
            input(
                attachmentPaths = listOf("/data/receipts/a.jpg"),
                existingPaths = setOf("/data/receipts/a.jpg"),
            )
        )

        assertThat(report.missingAttachmentCount).isEqualTo(0)
        assertThat(report.hasIssues).isFalse()
    }

    @Test
    fun `duplicate count above zero flags issues`() {
        val report = scanner.scan(input(duplicates = 2))

        assertThat(report.duplicateClientGeneratedIdCount).isEqualTo(2)
        assertThat(report.hasIssues).isTrue()
    }

    @Test
    fun `median confidence odd count picks middle value`() {
        val report = scanner.scan(input(confidences = listOf(90, 40, 70)))

        assertThat(report.medianSmsConfidence).isEqualTo(java.math.BigDecimal(70))
    }

    @Test
    fun `median confidence even count averages middles`() {
        val report = scanner.scan(input(confidences = listOf(90, 40)))

        assertThat(report.medianSmsConfidence)
            .isEqualTo(java.math.BigDecimal("65.0"))
    }

    @Test
    fun `median confidence rounds half up to one decimal`() {
        val report = scanner.scan(input(confidences = listOf(51, 52)))

        assertThat(report.medianSmsConfidence)
            .isEqualTo(java.math.BigDecimal("51.5"))
    }

    @Test
    fun `empty confidences yield null median`() {
        val report = scanner.scan(input(confidences = emptyList()))

        assertThat(report.medianSmsConfidence).isNull()
    }

    @Test
    fun `oldest date millis converts to local date`() {
        val date = LocalDate.of(2026, 3, 15)
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val report = scanner.scan(input(oldest = millis))

        assertThat(report.oldestTransactionDate).isEqualTo(date)
    }

    @Test
    fun `null oldest date stays null`() {
        val report = scanner.scan(input(oldest = null))

        assertThat(report.oldestTransactionDate).isNull()
    }
}
