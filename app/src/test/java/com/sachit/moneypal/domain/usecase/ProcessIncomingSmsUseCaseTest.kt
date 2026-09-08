package com.sachit.moneypal.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.model.BudgetPeriod
import com.sachit.moneypal.domain.model.BudgetSettings
import com.sachit.moneypal.domain.model.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalDateTime

class ProcessIncomingSmsUseCaseTest {

    private lateinit var budgetRepository: BudgetRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: ProcessIncomingSmsUseCase

    private val today: LocalDate = LocalDate.now()

    private val budgetSettings = BudgetSettings(
        totalBudget = BigDecimal("3000"),
        period = BudgetPeriod.MONTHLY,
        startDate = today.withDayOfMonth(1),
        // Explicit endDate so the period is open today regardless of month length
        // (the 30-day convention would otherwise close it on the 31st).
        endDate = today.plusDays(1),
        currencyCode = "INR",
    )

    @Before
    fun setUp() {
        budgetRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        coEvery { settingsRepository.getSettings() } returns UserSettings(smsCaptureEnabled = true)
        coEvery { settingsRepository.isSmsSeen(any()) } returns false
        coEvery { budgetRepository.getBudgetSettingsSync() } returns budgetSettings
        coEvery { settingsRepository.getCurrentPeriodId() } returns 42L
        useCase = ProcessIncomingSmsUseCase(budgetRepository, settingsRepository)
    }

    /** Epoch millis for local noon today — always inside the current monthly period. */
    private val ts: Long = LocalDateTime.of(today, java.time.LocalTime.NOON)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    @Test
    fun `ignores when capture disabled`() = runTest {
        coEvery { settingsRepository.getSettings() } returns UserSettings(smsCaptureEnabled = false)

        val result = useCase("HDFC-BANK", "Debited Rs 500 from account", ts)

        assertThat(result).isEqualTo(ProcessIncomingSmsUseCase.Result.Ignored)
        coVerify(exactly = 0) { budgetRepository.addTransaction(any()) }
    }

    @Test
    fun `debit inserts expense with sender as comment`() = runTest {
        val result = useCase("HDFC-BANK", "Your a/c XX1234 is debited for Rs 500", ts)

        assertThat(result).isInstanceOf(ProcessIncomingSmsUseCase.Result.Captured::class.java)
        val captured = result as ProcessIncomingSmsUseCase.Result.Captured
        assertThat(captured.isCredit).isFalse()
        assertThat(captured.amount).isEqualTo(BigDecimal("500"))

        val txSlot = slot<com.sachit.moneypal.domain.model.Transaction>()
        coVerify(exactly = 1) { budgetRepository.addTransaction(capture(txSlot)) }
        val tx = txSlot.captured
        assertThat(tx.amount).isEqualTo(BigDecimal("500"))
        assertThat(tx.comment).isEqualTo("HDFC-BANK")
        assertThat(tx.isAdjustment).isFalse()
        assertThat(tx.periodId).isEqualTo(42L)
    }

    @Test
    fun `credit inserts budget adjustment with negated amount`() = runTest {
        val result = useCase("HDFC-BANK", "Rs 1000 credited to your account XX1234", ts)

        assertThat(result).isInstanceOf(ProcessIncomingSmsUseCase.Result.Captured::class.java)
        val captured = result as ProcessIncomingSmsUseCase.Result.Captured
        assertThat(captured.isCredit).isTrue()

        val txSlot = slot<com.sachit.moneypal.domain.model.Transaction>()
        coVerify(exactly = 1) { budgetRepository.addTransaction(capture(txSlot)) }
        val tx = txSlot.captured
        assertThat(tx.amount).isEqualTo(BigDecimal("-1000"))
        assertThat(tx.isAdjustment).isTrue()
    }

    @Test
    fun `non money sms is ignored`() = runTest {
        val result = useCase("VK-OTP", "Your OTP is 123456", ts)
        assertThat(result).isEqualTo(ProcessIncomingSmsUseCase.Result.Ignored)
        coVerify(exactly = 0) { budgetRepository.addTransaction(any()) }
    }

    @Test
    fun `duplicate sms is ignored`() = runTest {
        coEvery { settingsRepository.isSmsSeen(any()) } returns true

        val result = useCase("HDFC-BANK", "Debited Rs 500 from account", ts)

        assertThat(result).isEqualTo(ProcessIncomingSmsUseCase.Result.Ignored)
        coVerify(exactly = 0) { budgetRepository.addTransaction(any()) }
    }

    @Test
    fun `marks sms seen after successful insert`() = runTest {
        useCase("HDFC-BANK", "Debited Rs 500 from account", ts)
        coVerify(exactly = 1) { settingsRepository.markSmsSeen(any()) }
    }

    @Test
    fun `does not mark seen when insert fails`() = runTest {
        coEvery { budgetRepository.addTransaction(any()) } throws RuntimeException("db down")

        val result = useCase("HDFC-BANK", "Debited Rs 500 from account", ts)

        assertThat(result).isInstanceOf(ProcessIncomingSmsUseCase.Result.Error::class.java)
        coVerify(exactly = 0) { settingsRepository.markSmsSeen(any()) }
    }

    @Test
    fun `queues when past period end`() = runTest {
        val staleSettings = budgetSettings.copy(
            startDate = today.minusMonths(1).withDayOfMonth(1),
            endDate = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth()),
        )
        coEvery { budgetRepository.getBudgetSettingsSync() } returns staleSettings

        val result = useCase("HDFC-BANK", "Debited Rs 500 from account", ts)

        assertThat(result).isInstanceOf(ProcessIncomingSmsUseCase.Result.Captured::class.java)
        val txSlot = slot<com.sachit.moneypal.domain.model.Transaction>()
        coVerify(exactly = 1) { budgetRepository.addQueuedTransaction(capture(txSlot)) }
        coVerify(exactly = 0) { budgetRepository.addTransaction(any()) }
        assertThat(txSlot.captured.periodId).isEqualTo(0L)
    }
}
