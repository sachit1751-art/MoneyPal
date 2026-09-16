package com.sachit.moneypal.wearsync

import com.sachit.moneypal.presentation.ui.budget.BudgetUiState

/**
 * Flavor seam for pushing budget state to Wear (plan 010). Main-source
 * [com.sachit.moneypal.presentation.ui.budget.BudgetWidgetUpdater] fires every
 * bound hook when the budget state changes; the foss flavor contributes a
 * no-op binding, the wear flavor contributes the real publisher-backed one.
 */
interface WearBudgetStateHook {
    suspend fun onBudgetStateChanged(state: BudgetUiState)
}
