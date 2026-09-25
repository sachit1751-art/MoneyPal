package com.sachit.moneypal.presentation.ui.settings.datahealth

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.data.repository.DataHealthRepository
import com.sachit.moneypal.domain.datahealth.DataHealthReport
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DataHealthViewModelTest {

    private val dataHealthRepository: DataHealthRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DataHealthViewModel(dataHealthRepository)

    private fun report(
        missing: Int = 0,
        duplicates: Int = 0,
    ) = DataHealthReport(
        totalTransactions = 10,
        incomeCount = 2,
        expenseCount = 8,
        smsCapturedCount = 4,
        medianSmsConfidence = BigDecimal("90"),
        attachmentCount = 3,
        missingAttachmentCount = missing,
        duplicateClientGeneratedIdCount = duplicates,
        oldestTransactionDate = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `initial state is loading then success with report`() = runTest {
        coEvery { dataHealthRepository.scan() } returns report()

        createViewModel().uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            val loaded = awaitItem()
            assertThat(loaded.isLoading).isFalse()
            assertThat(loaded.report).isNotNull()
            assertThat(loaded.loadFailed).isFalse()
        }
    }

    @Test
    fun `clean report maps to no issues`() = runTest {
        coEvery { dataHealthRepository.scan() } returns report()

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.report?.hasIssues).isFalse()
    }

    @Test
    fun `missing attachments map to issue state`() = runTest {
        coEvery { dataHealthRepository.scan() } returns report(missing = 2)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.report?.missingAttachmentCount).isEqualTo(2)
        assertThat(state.report?.hasIssues).isTrue()
    }

    @Test
    fun `duplicate ids map to issue state`() = runTest {
        coEvery { dataHealthRepository.scan() } returns report(duplicates = 1)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.report?.duplicateClientGeneratedIdCount).isEqualTo(1)
        assertThat(state.report?.hasIssues).isTrue()
    }

    @Test
    fun `scan failure surfaces loadFailed state`() = runTest {
        coEvery { dataHealthRepository.scan() } throws RuntimeException("boom")

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.loadFailed).isTrue()
        assertThat(state.report).isNull()
    }

    @Test
    fun `refresh reloads the report`() = runTest {
        coEvery { dataHealthRepository.scan() } returns report()
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        coEvery { dataHealthRepository.scan() } returns report(missing = 1)
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.report?.missingAttachmentCount).isEqualTo(1)
    }
}
