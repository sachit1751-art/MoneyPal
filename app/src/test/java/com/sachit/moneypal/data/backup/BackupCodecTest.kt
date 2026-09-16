package com.sachit.moneypal.data.backup

import com.sachit.moneypal.domain.model.SKIPPED_OCCURRENCE_MARKER
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
                pausedAtEpochMs = 1_770_000_000_000, // plan 008 paused recurring
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
        paidOccurrences = listOf(
            BackupPaidOccurrence(transactionId = 3, occurrenceDateEpochDay = 20_000),
            BackupPaidOccurrence(
                transactionId = 3,
                occurrenceDateEpochDay = 20_030,
                paidAt = SKIPPED_OCCURRENCE_MARKER, // plan 008 skipped occurrence
            ),
        ),
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

    // ---- Encrypted backup support (plan 011) ----

    @Test
    fun `encrypted round trip preserves all fields`() {
        val password = "strong pass".toCharArray()
        val original = sampleBackup()
        val decoded = BackupCodec.decode(
            BackupCodec.encodeEncrypted(original, password),
            password,
        )
        assertEquals(original, decoded)
    }

    @Test
    fun `encrypted file without password reports password protected`() {
        val encoded = BackupCodec.encodeEncrypted(sampleBackup(), "pw123456".toCharArray())
        val exception = assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(encoded)
        }
        assertEquals(BackupCodec.BACKUP_PASSWORD_REQUIRED_MESSAGE, exception.message)
    }

    @Test
    fun `encrypted file with wrong password fails cleanly`() {
        val encoded = BackupCodec.encodeEncrypted(sampleBackup(), "pw123456".toCharArray())
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(encoded, "wrong-pw".toCharArray())
        }
    }

    @Test
    fun `plaintext still decodes when a password is supplied`() {
        val decoded = BackupCodec.decode(
            BackupCodec.encode(sampleBackup()),
            "irrelevant".toCharArray(),
        )
        assertEquals(sampleBackup(), decoded)
    }

    @Test
    fun `garbage throws BackupFormatException`() {
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("random bytes", null)
        }
    }

    // ---- Pause / skip round trip (plan 008) ----

    @Test
    fun `pause and skip state survive a round trip`() {
        val original = sampleBackup()
        val decoded = BackupCodec.decode(BackupCodec.encode(original))

        assertEquals(1_770_000_000_000L, decoded.transactions.last { it.isRecurrent }.pausedAtEpochMs)
        assertEquals(2, decoded.paidOccurrences.size)
        assertEquals(
            SKIPPED_OCCURRENCE_MARKER,
            decoded.paidOccurrences.last().paidAt,
        )
    }

    @Test
    fun `old v1 file without pause or skip fields still decodes`() {
        val json = BackupCodec.encode(sampleBackup())
        // Strip the plan 008 keys to simulate a pre-008 v1 backup file.
        val legacyJson = json
            .replace(",\"pausedAtEpochMs\":1770000000000", "")
            .replace(",\"paidAt\":-1", "")
        val decoded = BackupCodec.decode(legacyJson)

        assertEquals(null, decoded.transactions.last { it.isRecurrent }.pausedAtEpochMs)
        assertEquals(0L, decoded.paidOccurrences.first().paidAt)
    }
}
