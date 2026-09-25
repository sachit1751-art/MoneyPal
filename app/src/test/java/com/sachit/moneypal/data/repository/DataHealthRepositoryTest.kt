package com.sachit.moneypal.data.repository

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.data.attachments.AttachmentStore
import com.sachit.moneypal.data.local.dao.TransactionDao
import com.sachit.moneypal.domain.datahealth.DataHealthScanner
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/**
 * Plan-049 repository scan on the JVM: DAO count-only queries are stubbed,
 * the receipt-existence check runs against a real temp directory, and the
 * pure [DataHealthScanner] does the aggregation.
 */
class DataHealthRepositoryTest {

    private lateinit var transactionDao: TransactionDao
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var attachmentsDir: File
    private lateinit var repository: DataHealthRepository

    @Before
    fun setUp() {
        transactionDao = mockk()
        attachmentStore = mockk()
        attachmentsDir = File(
            System.getProperty("java.io.tmpdir"),
            "moneypal-data-health-test-${System.nanoTime()}",
        ).apply { mkdirs() }
        every { attachmentStore.attachmentsDir() } returns attachmentsDir
        repository = DataHealthRepository(transactionDao, attachmentStore, DataHealthScanner())
    }

    @After
    fun tearDown() {
        attachmentsDir.deleteRecursively()
    }

    private fun stubCounts(
        total: Int = 0,
        income: Int = 0,
        sms: Int = 0,
        confidences: List<Int> = emptyList(),
        attachmentUris: List<String> = emptyList(),
        duplicates: Int = 0,
        oldest: Long? = null,
    ) {
        coEvery { transactionDao.countActive() } returns total
        coEvery { transactionDao.countIncome() } returns income
        coEvery { transactionDao.countSmsCaptured() } returns sms
        coEvery { transactionDao.smsConfidences() } returns confidences
        coEvery { transactionDao.attachmentUris() } returns attachmentUris
        coEvery { transactionDao.countDuplicateClientGeneratedIds() } returns duplicates
        coEvery { transactionDao.oldestDateMillis() } returns oldest
    }

    @Test
    fun `empty scan produces zeroed report`() = runTest {
        stubCounts()

        val report = repository.scan()

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
    fun `counts and oldest date pass through the scanner`() = runTest {
        val oldest = LocalDate.of(2026, 3, 15)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        stubCounts(total = 5, income = 1, sms = 2, confidences = listOf(90, 40), oldest = oldest)

        val report = repository.scan()

        assertThat(report.totalTransactions).isEqualTo(5)
        assertThat(report.incomeCount).isEqualTo(1)
        assertThat(report.expenseCount).isEqualTo(4)
        assertThat(report.smsCapturedCount).isEqualTo(2)
        assertThat(report.medianSmsConfidence).isEqualTo(BigDecimal("65.0"))
        assertThat(report.oldestTransactionDate).isEqualTo(LocalDate.of(2026, 3, 15))
    }

    @Test
    fun `missing receipt file is detected as an issue`() = runTest {
        val existing = File(attachmentsDir, "existing.jpg").apply { writeText("x") }
        val missing = File(attachmentsDir, "missing.jpg").absolutePath
        stubCounts(
            total = 2,
            attachmentUris = listOf(existing.absolutePath, missing),
        )

        val report = repository.scan()

        assertThat(report.attachmentCount).isEqualTo(2)
        assertThat(report.missingAttachmentCount).isEqualTo(1)
        assertThat(report.hasIssues).isTrue()
    }

    @Test
    fun `paths outside the receipts dir never count as present`() = runTest {
        val foreign = File(
            attachmentsDir.parentFile ?: File("."),
            "elsewhere.jpg",
        ).apply { writeText("x") }
        stubCounts(total = 1, attachmentUris = listOf(foreign.absolutePath))
        foreign.deleteOnExit()

        val report = repository.scan()

        assertThat(report.attachmentCount).isEqualTo(1)
        assertThat(report.missingAttachmentCount).isEqualTo(1)
        assertThat(report.hasIssues).isTrue()
    }

    @Test
    fun `all receipts present means no issues despite duplicates being zero`() = runTest {
        val existing = File(attachmentsDir, "kept.jpg").apply { writeText("x") }
        stubCounts(
            total = 1,
            attachmentUris = listOf(existing.absolutePath),
            duplicates = 0,
        )

        val report = repository.scan()

        assertThat(report.missingAttachmentCount).isEqualTo(0)
        assertThat(report.hasIssues).isFalse()
    }

    @Test
    fun `duplicate ids above zero flag issues`() = runTest {
        stubCounts(duplicates = 3)

        val report = repository.scan()

        assertThat(report.duplicateClientGeneratedIdCount).isEqualTo(3)
        assertThat(report.hasIssues).isTrue()
    }
}
