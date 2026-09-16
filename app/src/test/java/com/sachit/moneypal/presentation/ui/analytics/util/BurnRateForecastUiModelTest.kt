package com.sachit.moneypal.presentation.ui.analytics.util

import com.google.common.truth.Truth.assertThat
import com.sachit.moneypal.domain.model.BudgetState
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BurnRateForecastUiModelTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 16)

    /** BudgetState with only the forecast-relevant fields varied. */
    private fun state(
        pacePercent: Int,
        projectedOverspend: BigDecimal = BigDecimal.ZERO,
    ) = BudgetState.EMPTY.copy(pacePercent = pacePercent, projectedOverspend = projectedOverspend)

    @Test
    fun `on pace maps when pace at or below 100`() {
        val model = BurnRateForecastUiModel.from(
            state = state(pacePercent = 80),
            isHistoricalView = false,
            today = today,
        )

        assertThat(model).isNotNull()
        assertThat(model!!.onPace).isTrue()
    }

    @Test
    fun `over pace maps when pace above 100`() {
        val model = BurnRateForecastUiModel.from(
            state = state(pacePercent = 140, projectedOverspend = BigDecimal("1850")),
            isHistoricalView = false,
            today = today,
        )

        assertThat(model).isNotNull()
        assertThat(model!!.onPace).isFalse()
        assertThat(model.projectedOverspend).isEqualTo(BigDecimal("1850"))
    }

    @Test
    fun `hidden when fresh period has no pace signal`() {
        val model = BurnRateForecastUiModel.from(
            state = state(pacePercent = 0),
            isHistoricalView = false,
            today = today,
        )

        assertThat(model).isNull()
    }

    @Test
    fun `hidden for past periods`() {
        val model = BurnRateForecastUiModel.from(
            state = state(pacePercent = 140, projectedOverspend = BigDecimal("100")),
            isHistoricalView = true,
            today = today,
        )

        assertThat(model).isNull()
    }

    @Test
    fun `hidden when no budget state`() {
        assertThat(BurnRateForecastUiModel.from(null, isHistoricalView = false, today = today)).isNull()
    }
}
