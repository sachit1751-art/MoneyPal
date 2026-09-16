package com.sachit.moneypal.wearsync

import com.sachit.moneypal.presentation.ui.budget.BudgetUiState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foss flavor: no Wear bridge, nothing to publish. Implements the main-source
 * [WearBudgetStateHook] seam as a no-op.
 */
@Singleton
class NoopWearBudgetStateHook @Inject constructor() : WearBudgetStateHook {
    override suspend fun onBudgetStateChanged(state: BudgetUiState) = Unit
}
