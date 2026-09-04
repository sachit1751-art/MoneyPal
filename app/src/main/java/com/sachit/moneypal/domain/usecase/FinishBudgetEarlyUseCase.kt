package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.presentation.ui.budget.BudgetPeriodManager
import javax.inject.Inject

class FinishBudgetEarlyUseCase @Inject constructor(
    private val periodManager: BudgetPeriodManager,
) {
    suspend operator fun invoke() {
        periodManager.finishBudgetEarly()
    }
}
