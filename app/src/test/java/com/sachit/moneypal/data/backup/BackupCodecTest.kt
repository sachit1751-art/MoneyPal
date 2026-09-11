package com.sachit.moneypal.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun sampleBackup(): MoneyPalBackup = MoneyPalBackup(
        exportedAtEpochMs = 1_760_000_000_000,
        transactions = listOf(
            BackupTransaction(
                id = 1,
                amount = "45.50",
                comment = "Groceries",
                date = 1_760_000_000_000,
                createdAt = 1_760_000_000_000,
                clientGeneratedId = "abc-123",
                periodId = 7,
                isRecurrent = false,
                categoryId = 2,
                isAdjustment = false,
                attachmentUri = "/data/receipts/receipt_1.jpg",
                originalAmount = "500.00",
                originalCurrency = "INR",
            ),
            BackupTransaction(
                id = 2,
                amount = "0",
                comment = "",
                date = 0,
                isDeleted = true, // soft-deleted rows survive in the backup
            ),
            BackupTransaction(
                id = 3,
                amount = "10.00",
                comment = "Sub",
                date = 1_760_000_000_000,
                isRecurrent = true,
                recurrentFrequency = "MONTHLY",
                recurrentEndDate = 1_780_000_000_000,
                subscriptionDay = 15,
            ),
        ),
        categories = listOf(
            BackupCategory(name = "Food", usageCount = 12, lastUsedAt = 42L, isHidden = false),
            BackupCategory(name = "Old", usageCount = 1, isHidden = true, createdAt = 7L),
        ),
        archivedBudgets = listOf(
            BackupArchivedBudget(
                periodId = 7,
                totalBudget = "3000.00",
                spentAmount = "2800.00",
                startDate = "2026-09-01",
                endDate = "2026-09-30",
                currencyCode = "USD",
                periodType = "MONTHLY",
                createdAt = 10L,
            ),
        ),
        paidOccurrences = listOf(BackupPaidOccurrence(transactionId = 3, occurrenceDateEpochDay = 20_000)),
        budgetSettings = BackupBudgetSettings(
            totalBudget = "3000.00",
            period = "MONTHLY",
            startDate = "2026-09-01",
            endDate = "2026-09-30",
            currencyCode = "USD",
            daysInPeriod = 30,
            rollOverEnabled = true,
            rollOverLimit = "100.00",
            rollOverCarryForward = false,
            remainingBudgetStrategy = "SPLIT_EQUALLY",
            creditCardCutoffDay = 15,
            splitMode = "DYNAMIC",
        ),
        settings = BackupSettings(
            themeMode = "NIGHT",
            language = "es",
            amoledEnabled = true,
            savingsGoalAmount = "5000.00",
            savingsGoalMonths = 12,
        ),
    )

    @Test
    fun `round trip preserves all fields`() {
        val original = sampleBackup()
        val decoded = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `unknown keys are ignored for forward compatibility`() {
        val json = BackupCodec.encode(sampleBackup())
        val withExtraKeys = json.replaceFirst(
            "{",
            "{\"someFutureField\": 123,",
        )
        val decoded = BackupCodec.decode(withExtraKeys)
        assertEquals(sampleBackup(), decoded)
    }

    @Test
    fun `corrupt json throws BackupFormatException`() {
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("this is not json {")
        }
    }

    @Test
    fun `newer schema version is rejected`() {
        val newer = sampleBackup().copy(schemaVersion = 99)
        val raw = BackupCodec.encode(newer)
        val exception = assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(raw)
        }
        assertTrue(exception.message!!.contains("newer"))
    }

    @Test
    fun `schema version 1 decodes fine`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sampleBackup()))
        assertEquals(1, decoded.schemaVersion)
    }
}
