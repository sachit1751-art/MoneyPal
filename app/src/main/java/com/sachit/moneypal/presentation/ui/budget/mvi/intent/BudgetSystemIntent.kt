package com.sachit.moneypal.presentation.ui.budget.mvi.intent

import com.sachit.moneypal.presentation.ui.budget.mvi.BudgetUiIntent

sealed interface BudgetSystemIntent : BudgetUiIntent {
    data object MarkFirstLaunchComplete : BudgetSystemIntent
    data class SetLockSwipeable(val locked: Boolean) : BudgetSystemIntent
    data class SetLockDraggable(val locked: Boolean) : BudgetSystemIntent
}
