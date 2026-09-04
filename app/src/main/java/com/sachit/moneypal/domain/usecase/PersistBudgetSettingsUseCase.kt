package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.domain.model.BudgetSettings
import com.sachit.moneypal.presentation.ui.budget.BudgetPeriodManager
import com.sachit.moneypal.presentation.ui.budget.PeriodBoundaryResult
import javax.inject.Inject

class PersistBudgetSettingsUseCase @Inject constructor(
    private val periodManager: BudgetPeriodManager,
) {
    suspend operator fun invoke(
        settings: BudgetSettings,
        forceNewPeriodBoundary: Boolean,
    ): PeriodBoundaryResult {
        return periodManager.persistBudgetSettings(
            settings = settings,
            forceNewPeriodBoundary = forceNewPeriodBoundary,
        )
    }
}
