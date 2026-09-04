package com.sachit.moneypal.presentation.ui.budget.mvi.intent

import com.sachit.moneypal.domain.model.BudgetSettings
import com.sachit.moneypal.domain.model.RecurrentFrequency
import com.sachit.moneypal.presentation.ui.budget.mvi.BudgetUiIntent
import com.sachit.moneypal.presentation.ui.editor.AnimState
import com.sachit.moneypal.presentation.ui.editor.EditMode
import java.time.LocalDate

sealed interface BudgetEditorIntent : BudgetUiIntent {
    data class DateSelected(val date: LocalDate) : BudgetEditorIntent
    data class UpdateSettings(val settings: BudgetSettings) : BudgetEditorIntent
    data class SetEditMode(val mode: EditMode) : BudgetEditorIntent
    data class SetAnimState(val state: AnimState) : BudgetEditorIntent
    data class CommentUpdated(val comment: String) : BudgetEditorIntent
    data class DeleteTag(val tag: String) : BudgetEditorIntent
    data class SetRecurrentEnabled(val enabled: Boolean) : BudgetEditorIntent
    data class SetCreditEnabled(val enabled: Boolean) : BudgetEditorIntent
    data object DismissRecurrentDialog : BudgetEditorIntent
    data object DismissCreditCutoffDialog : BudgetEditorIntent
    data class RecurrentExpenseApplied(
        val frequency: RecurrentFrequency,
        val endDate: LocalDate,
        val subscriptionDay: Int? = null,
        val fallbackComment: String,
    ) : BudgetEditorIntent
    data class CreditCutoffDayConfirmed(val cutoffDay: Int) : BudgetEditorIntent
    data object FinishBudgetEarly : BudgetEditorIntent
}
