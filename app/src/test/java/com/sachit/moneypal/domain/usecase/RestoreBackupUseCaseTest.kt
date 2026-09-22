package com.sachit.moneypal.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.data.backup.BackupCategory
import com.sachit.moneypal.data.backup.BackupPaidOccurrence
import com.sachit.moneypal.data.backup.BackupTransaction
import com.sachit.moneypal.data.backup.MoneyPalBackup
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.model.BudgetPeriod
import com.sachit.moneypal.domain.model.BudgetSettings
import com.sachit.moneypal.domain.model.Category
import com.sachit.moneypal.domain.model.PaymentMethod
import com.sachit.moneypal.domain.model.SKIPPED_OCCURRENCE_MARKER
import com.sachit.moneypal.domain.model.SavingsPreferences
import com.sachit.moneypal.domain.model.ThemeMode
import com.sachit.moneypal.domain.model.Transaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Characterization tests for the restore path (plan 028): each test pins what
 * the code DOES today, including known-buggy behavior. Tests annotated
 * `PLAN-025: pinned-to-change` document behavior plan 025 deliberately
 * changes (id remapping); all others are regression guards.
 */
class RestoreBackupUseCaseTest {

    private lateinit var budgetRepository: BudgetRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: RestoreBackupUseCase

    private val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
    private val localTransactions = mutableListOf<Transaction>()

    private val upsertedCategories = slot<List<Category>>()
    private val upsertedTransactions = slot<List<Transaction>>()
    private var fakeRestoredIdCounter = 100L
    private val savedBudgetSettings = slot<BudgetSettings>()
    private val savedSavingsPreferences = slot<SavingsPreferences>()

    @Before
    fun setUp() = runTest {
        budgetRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        useCase = RestoreBackupUseCase(budgetRepository, settingsRepository)

        coEvery { budgetRepository.getAllCategories() } returns categoriesFlow
        coEvery { budgetRepository.getAllTransactionsIncludingDeleted() } returns localTransactions
        coEvery { budgetRepository.existsTransactionByClientGeneratedId(any()) } returns false
        coEvery { budgetRepository.upsertCategories(capture(upsertedCategories)) } returns Unit
        coEvery { budgetRepository.upsertTransactions(capture(upsertedTransactions)) } answers {
            // Fake Room: assigns a fresh row id per restored row (plan 025).
            arg<List<Transaction>>(0).map { ++fakeRestoredIdCounter }
        }
        coEvery {
            budgetRepository.findTransactionIdByClientGeneratedId(any())
        } answers {
            // Mirror of the fake DB: rows "exist" only if the test registered them.
            localTransactions.firstOrNull { it.clientGeneratedId == firstArg<String>() }?.id
        }
        coEvery {
            budgetRepository.saveBudgetSettings(capture(savedBudgetSettings))
        } returns Unit
        coEvery {
            settingsRepository.setSavingsPreferences(capture(savedSavingsPreferences))
        } returns Unit
    }

    /** Epoch-millis fixture matching the backup storage format. */
    private fun epochMilli(dateTime: LocalDateTime): Long =
        dateTime.toEpochSecond(ZoneOffset.UTC) * 1000

    private val fixtureDate: LocalDateTime = LocalDateTime.ofEpochSecond(
        1_700_000_000, 0, ZoneOffset.UTC
    )

    private fun backupTx(
        id: Long = 0,
        amount: String = "10.00",
        comment: String = "Coffee",
        date: Long = epochMilli(fixtureDate),
        clientGeneratedId: String? = null,
        categoryId: Long? = null,
        paymentMethod: String = "OTHER",
    ) = BackupTransaction(
        id = id,
        amount = amount,
        comment = comment,
        date = date,
        clientGeneratedId = clientGeneratedId,
        categoryId = categoryId,
        paymentMethod = paymentMethod,
    )

    @Test
    fun `fresh install restores all transactions with id 0`() = runTest {
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            transactions = listOf(
                backupTx(amount = "10.00"),
                backupTx(amount = "20.00", comment = "Lunch"),
            ),
        )

        val result = useCase(backup)

        assertThat(upsertedTransactions.captured).hasSize(2)
        assertThat(upsertedTransactions.captured.all { it.id == 0L }).isTrue()
        assertThat(result.transactionsRestored).isEqualTo(2)
        assertThat(result.transactionsSkipped).isEqualTo(0)
    }

    @Test
    fun `duplicate clientGeneratedId is skipped`() = runTest {
        coEvery {
            budgetRepository.existsTransactionByClientGeneratedId("cg-1")
        } returns true
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            transactions = listOf(backupTx(clientGeneratedId = "cg-1")),
        )

        val result = useCase(backup)

        assertThat(result.transactionsSkipped).isEqualTo(1)
        assertThat(result.transactionsRestored).isEqualTo(0)
        coVerify(exactly = 0) { budgetRepository.upsertTransactions(any()) }
    }

    @Test
    fun `triple-match duplicate is skipped`() = runTest {
        localTransactions += Transaction(
            amount = BigDecimal("10.00"),
            comment = "Coffee",
            date = fixtureDate,
        )
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            transactions = listOf(backupTx()), // same date/amount/comment
        )

        val result = useCase(backup)

        assertThat(result.transactionsSkipped).isEqualTo(1)
        coVerify(exactly = 0) { budgetRepository.upsertTransactions(any()) }
    }

    @Test
    fun `categories unchanged are skipped, changed preserve id`() = runTest {
        categoriesFlow.value = listOf(Category(id = 7, name = "Food", usageCount = 3))
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            categories = listOf(
                BackupCategory(name = "Food", usageCount = 3), // unchanged
                BackupCategory(name = "Food", usageCount = 9), // changed
            ),
        )

        val unchangedResult = useCase(
            MoneyPalBackup(
                exportedAtEpochMs = 0,
                categories = listOf(BackupCategory(name = "Food", usageCount = 3)),
            ),
        )
        assertThat(unchangedResult.categoriesRestored).isEqualTo(0)
        coVerify(exactly = 0) { budgetRepository.upsertCategories(any()) }

        categoriesFlow.value = listOf(Category(id = 7, name = "Food", usageCount = 3))
        val changedResult = useCase(
            MoneyPalBackup(
                exportedAtEpochMs = 0,
                categories = listOf(BackupCategory(name = "Food", usageCount = 9)),
            ),
        )
        assertThat(changedResult.categoriesRestored).isEqualTo(1)
        assertThat(upsertedCategories.captured.single().id).isEqualTo(7) // existing id preserved
    }

    @Test // plan 025: occurrence remapped to the restored row id
    fun `occurrence remapped to the restored row id`() = runTest {
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            transactions = listOf(backupTx(id = 42)),
            paidOccurrences = listOf(
                BackupPaidOccurrence(transactionId = 42, occurrenceDateEpochDay = 20_000),
            ),
        )

        val result = useCase(backup)

        // The mark must use the fake-upsert-assigned id (101), never the
        // backup's source-device id 42.
        coVerify(exactly = 1) {
            budgetRepository.markRecurrentOccurrencePaid(101L, LocalDate.ofEpochDay(20_000))
        }
        coVerify(exactly = 0) {
            budgetRepository.markRecurrentOccurrencePaid(42L, any())
        }
        assertThat(result.paidOccurrencesRestored).isEqualTo(1)
        assertThat(result.paidOccurrencesSkipped).isEqualTo(0)
    }

    @Test // plan 025: unresolvable occurrences are never marked
    fun `unresolvable occurrence is skipped, not marked`() = runTest {
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            transactions = emptyList(), // tx 42 was never in the list
            paidOccurrences = listOf(
                BackupPaidOccurrence(transactionId = 42, occurrenceDateEpochDay = 20_000),
            ),
        )

        val result = useCase(backup)

        coVerify(exactly = 0) { budgetRepository.markRecurrentOccurrencePaid(any(), any()) }
        coVerify(exactly = 0) { budgetRepository.markOccurrenceSkipped(any(), any()) }
        assertThat(result.paidOccurrencesSkipped).isEqualTo(1)
        assertThat(result.paidOccurrencesRestored).isEqualTo(0)
    }

    @Test // plan 025: deduped-by-cgid occurrences map to the pre-existing row
    fun `occurrence maps to the pre-existing row when deduped by clientGeneratedId`() = runTest {
        localTransactions += Transaction(
            amount = BigDecimal("10.00"),
            comment = "Coffee",
            date = fixtureDate,
            clientGeneratedId = "cg-existing",
        ).copy(id = 55L)
        // Dedupe is decided by existsTransactionByClientGeneratedId: this test
        // needs the cgid branch to fire (setup stubs it to false for the
        // triple-match tests).
        coEvery { budgetRepository.existsTransactionByClientGeneratedId("cg-existing") } returns true
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            transactions = listOf(backupTx(id = 42, clientGeneratedId = "cg-existing")),
            paidOccurrences = listOf(
                BackupPaidOccurrence(transactionId = 42, occurrenceDateEpochDay = 20_000),
            ),
        )

        val result = useCase(backup)

        coVerify(exactly = 1) {
            budgetRepository.markRecurrentOccurrencePaid(55L, LocalDate.ofEpochDay(20_000))
        }
        assertThat(result.paidOccurrencesRestored).isEqualTo(1)
    }

    @Test // plan 025: unmapped comment drops the stale category id
    fun `unmapped comment drops stale categoryId`() = runTest {
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            transactions = listOf(backupTx(categoryId = 99, comment = "")),
        )

        useCase(backup)

        // Losing a category link beats linking the WRONG category; the row is
        // intact and recategorization is one tap.
        assertThat(upsertedTransactions.captured.single().categoryId).isNull()
    }

    @Test
    fun `transaction categoryId resolved from comment when mapped`() = runTest {
        categoriesFlow.value = listOf(Category(id = 7, name = "Food"))
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            transactions = listOf(backupTx(comment = "Food", categoryId = 55)),
        )

        useCase(backup)

        // The comment-name heuristic refines a carried backup id to the local
        // category with the same name as the comment.
        assertThat(upsertedTransactions.captured.single().categoryId).isEqualTo(7)
    }

    @Test
    fun `budget settings not overwritten when local exists`() = runTest {
        coEvery { budgetRepository.getBudgetSettingsSync() } returns BudgetSettings(
            totalBudget = BigDecimal("1000"),
            period = BudgetPeriod.MONTHLY,
            startDate = LocalDate.now(),
        )
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            budgetSettings = com.sachit.moneypal.data.backup.BackupBudgetSettings(
                totalBudget = "3000",
                period = "MONTHLY",
                startDate = "2026-01-01",
            ),
        )

        val result = useCase(backup)

        coVerify(exactly = 0) { budgetRepository.saveBudgetSettings(any()) }
        assertThat(result.budgetSettingsRestored).isFalse()
    }

    @Test
    fun `budget settings restored on fresh install`() = runTest {
        coEvery { budgetRepository.getBudgetSettingsSync() } returns null
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            budgetSettings = com.sachit.moneypal.data.backup.BackupBudgetSettings(
                totalBudget = "3000",
                period = "WEEKLY",
                startDate = "2026-01-01",
                endDate = "2026-01-07",
                currencyCode = "INR",
            ),
        )

        val result = useCase(backup)

        coVerify(exactly = 1) { budgetRepository.saveBudgetSettings(any()) }
        assertThat(savedBudgetSettings.captured.totalBudget).isEqualTo(BigDecimal("3000"))
        assertThat(savedBudgetSettings.captured.period).isEqualTo(BudgetPeriod.WEEKLY)
        assertThat(result.budgetSettingsRestored).isTrue()
    }

    @Test
    fun `invalid enums fall back to defaults`() = runTest {
        coEvery { budgetRepository.getBudgetSettingsSync() } returns null
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            transactions = listOf(
                backupTx(comment = "Snack", paymentMethod = "JUNK"),
            ),
            budgetSettings = com.sachit.moneypal.data.backup.BackupBudgetSettings(
                totalBudget = "1000",
                period = "NOT_A_REAL_ENUM",
                startDate = "2026-01-01",
            ),
        )

        useCase(backup)

        assertThat(savedBudgetSettings.captured.period).isEqualTo(BudgetPeriod.MONTHLY)
        assertThat(upsertedTransactions.captured.single().paymentMethod).isEqualTo(PaymentMethod.OTHER)
    }

    @Test
    fun `settings block applies theme and savings`() = runTest {
        val backup = MoneyPalBackup(
            exportedAtEpochMs = 0,
            settings = com.sachit.moneypal.data.backup.BackupSettings(
                themeMode = "NIGHT",
                savingsGoalAmount = "100.50",
                savingsGoalMonths = 6,
            ),
        )

        val result = useCase(backup)

        coVerify(exactly = 1) { settingsRepository.setThemeMode(ThemeMode.NIGHT) }
        assertThat(savedSavingsPreferences.captured.savingsGoalAmount)
            .isEqualTo(BigDecimal("100.50"))
        assertThat(savedSavingsPreferences.captured.savingsGoalMonths).isEqualTo(6)
        assertThat(result.settingsRestored).isTrue()
    }

    @Test
    fun `empty backup yields all-zero result and no repo writes`() = runTest {
        val backup = MoneyPalBackup(exportedAtEpochMs = 0)

        val result = useCase(backup)

        assertThat(result.transactionsRestored).isEqualTo(0)
        assertThat(result.transactionsSkipped).isEqualTo(0)
        assertThat(result.categoriesRestored).isEqualTo(0)
        assertThat(result.archivedBudgetsRestored).isEqualTo(0)
        assertThat(result.paidOccurrencesRestored).isEqualTo(0)
        assertThat(result.budgetSettingsRestored).isFalse()
        assertThat(result.settingsRestored).isFalse()
        coVerify(exactly = 0) { budgetRepository.upsertTransactions(any()) }
        coVerify(exactly = 0) { budgetRepository.upsertCategories(any()) }
        coVerify(exactly = 0) { budgetRepository.upsertArchivedBudgets(any()) }
        coVerify(exactly = 0) { budgetRepository.markRecurrentOccurrencePaid(any(), any()) }
        coVerify(exactly = 0) { budgetRepository.markOccurrenceSkipped(any(), any()) }
    }
}
