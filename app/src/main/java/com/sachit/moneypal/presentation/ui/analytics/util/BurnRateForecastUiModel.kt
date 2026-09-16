package com.sachit.moneypal.presentation.ui.analytics.util

import com.sachit.moneypal.domain.model.BudgetState
import java.math.BigDecimal
import java.time.LocalDate

/**
 * UI mapping for the burn-rate forecast card (plan 018). Pure function so the
 * hide/show and copy rules are unit-testable; the card renders whatever this
 * returns — null means "hide the card entirely".
 *
 * Math lives in [com.sachit.moneypal.domain.calculator.BurnRateCalculator];
 * this only translates its output into display semantics.
 */
data class BurnRateForecastUiModel(
    val onPace: Boolean,
    val pacePercent: Int,
    val projectedOverspend: BigDecimal,
) {
    companion object {
        /**
         * Returns null when the forecast is meaningless:
         *  - historical (closed) period, or
         *  - fresh period with no pace signal yet (<1 day elapsed).
         */
        fun from(state: BudgetState?, isHistoricalView: Boolean, today: LocalDate): BurnRateForecastUiModel? {
            if (state == null || isHistoricalView) return null
            if (state.pacePercent <= 0) return null
            // Nothing to forecast when the budget is already exhausted AND pace
            // is low: the exhaustion date already passed.
            return BurnRateForecastUiModel(
                onPace = state.pacePercent <= 100,
                pacePercent = state.pacePercent,
                projectedOverspend = state.projectedOverspend,
            )
        }
    }
}
