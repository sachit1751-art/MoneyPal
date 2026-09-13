package com.sachit.moneypal.presentation.ui.budget.mvi.intent

import com.sachit.moneypal.presentation.ui.budget.mvi.BudgetUiIntent

sealed interface BudgetNumpadIntent : BudgetUiIntent {
    data class NumberTapped(val digit: String) : BudgetNumpadIntent
    data object DotTapped : BudgetNumpadIntent
    data object BackspaceTapped : BudgetNumpadIntent
    data object ApplyTapped : BudgetNumpadIntent
    /** User confirmed saving an entry that looked like a duplicate. */
    data object ConfirmDuplicateSaveTapped : BudgetNumpadIntent
    data object ResetInputTapped : BudgetNumpadIntent
    data class OperatorTapped(val operator: Char) : BudgetNumpadIntent
    data object EqualsTapped : BudgetNumpadIntent
    data class SetCalculationMode(val enabled: Boolean) : BudgetNumpadIntent
    data class SetDragProgress(val progress: Float) : BudgetNumpadIntent
    data object TriggerTestNotifications : BudgetNumpadIntent
    /** Quick-amount chip tapped: adds [amount] to the current input. */
    data class QuickAmountTapped(val amount: java.math.BigDecimal) : BudgetNumpadIntent
}
