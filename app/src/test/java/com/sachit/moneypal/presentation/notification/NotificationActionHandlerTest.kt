package com.sachit.moneypal.presentation.notification

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.domain.model.RecurrentFrequency
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.domain.usecase.GetCurrentPeriodIdUseCase
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.presentation.ui.budget.BudgetTransactionHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Plan 045: the notification action back-end must reuse the in-app mark-paid
 * path (no duplicated logic), delegate snooze to the scheduler without
 * touching occurrence state, and fail silent on unknown ids per the plan-029
 * background error contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationActionHandlerTest {

    private val budgetRepository: BudgetRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val budgetTransactionHandler: BudgetTransactionHandler = mockk(relaxed = true)
    private val notificationScheduler: NotificationScheduler = mockk(relaxed = true)

    private lateinit var handler: NotificationActionHandler

    private val template = Transaction(
        id = 42L,
        amount = BigDecimal("9.99"),
        comment = "Gym",
        date = LocalDateTime.of(2026, 9, 1, 0, 0),
        isRecurrent = true,
        recurrentFrequency = RecurrentFrequency.MONTHLY,
        subscriptionDay = 15,
    )

    @Before
    fun setUp() {
        coEvery { settingsRepository.getCurrentPeriodId() } returns 7L
        handler = NotificationActionHandler(
            budgetRepository = budgetRepository,
            budgetTransactionHandler = budgetTransactionHandler,
            getCurrentPeriodIdUseCase = GetCurrentPeriodIdUseCase(settingsRepository),
            notificationScheduler = notificationScheduler,
        )
    }

    @Test
    fun `mark paid delegates to the in-app handler with the notification's occurrence date`() = runTest {
        coEvery { budgetRepository.getTransactionById(42L) } returns template
        val occurrenceDate = LocalDate.of(2026, 9, 15)

        val settled = handler.markOccurrencePaid(42L, occurrenceDate)

        assertThat(settled).isTrue()
        coVerify {
            budgetTransactionHandler.markRecurrentOccurrencePaid(template, 7L, occurrenceDate)
        }
    }

    @Test
    fun `mark paid on an unknown transaction id is a silent no-op`() = runTest {
        coEvery { budgetRepository.getTransactionById(42L) } returns null
        coEvery { budgetRepository.getTransactions() } returns kotlinx.coroutines.flow.flowOf(
            emptyList<Transaction>()
        )

        val settled = handler.markOccurrencePaid(42L, LocalDate.of(2026, 9, 15))

        assertThat(settled).isFalse()
        coVerify(exactly = 0) {
            budgetTransactionHandler.markRecurrentOccurrencePaid(any(), any(), any())
        }
    }

    @Test
    fun `mark paid on a deleted or non-recurring row is a no-op`() = runTest {
        coEvery { budgetRepository.getTransactionById(42L) } returns template.copy(isDeleted = true)
        coEvery { budgetRepository.getTransactions() } returns kotlinx.coroutines.flow.flowOf(
            listOf(template.copy(isDeleted = true))
        )

        val settled = handler.markOccurrencePaid(42L, LocalDate.of(2026, 9, 15))

        assertThat(settled).isFalse()
        coVerify(exactly = 0) {
            budgetTransactionHandler.markRecurrentOccurrencePaid(any(), any(), any())
        }
    }

    @Test
    fun `snooze calls the scheduler and never writes occurrence state`() = runTest {
        coEvery { budgetRepository.getTransactionById(42L) } returns template

        val snoozed = handler.snoozeOccurrence(42L)

        assertThat(snoozed).isTrue()
        coVerifySequence {
            notificationScheduler.snoozeRecurrentExpenseNotification(template, 1L)
        }
        coVerify(exactly = 0) {
            budgetTransactionHandler.markRecurrentOccurrencePaid(any(), any(), any())
        }
        coVerify(exactly = 0) {
            budgetRepository.markOccurrenceSkipped(any(), any())
        }
    }

    @Test
    fun `snooze on an unknown transaction id is a silent no-op`() = runTest {
        coEvery { budgetRepository.getTransactionById(42L) } returns null
        coEvery { budgetRepository.getTransactions() } returns kotlinx.coroutines.flow.flowOf(
            emptyList<Transaction>()
        )

        val snoozed = handler.snoozeOccurrence(42L)

        assertThat(snoozed).isFalse()
        coVerify(exactly = 0) {
            notificationScheduler.snoozeRecurrentExpenseNotification(any(), any())
        }
    }
}
