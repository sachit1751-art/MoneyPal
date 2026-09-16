package com.sachit.moneypal.wearsync

import com.sachit.moneypal.presentation.ui.budget.BudgetUiState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wear flavor: pushes the current budget state to the watch tile on every
 * widget update (plan 010). Sends exactly what the phone UI renders rather
 * than recomputing, so watch and phone can never disagree. Best-effort —
 * absence of a watch never throws.
 */
@Singleton
class PublishWearBudgetStateHook @Inject constructor(
    private val budgetStatePublisher: BudgetStatePublisher,
) : WearBudgetStateHook {

    override suspend fun onBudgetStateChanged(state: BudgetUiState) {
        val payload = WearBudgetStateMapper.toPayload(
            state = state,
            currencyCode = state.budgetSettings?.currencyCode ?: "USD",
            nowMs = System.currentTimeMillis(),
        ) ?: return

        runCatching { budgetStatePublisher.send(payload) }
    }
}

/** Payload construction from UI state; kept pure for potential unit testing. */
internal object WearBudgetStateMapper {

    fun toPayload(
        state: BudgetUiState,
        currencyCode: String,
        nowMs: Long,
    ): com.sachit.moneypal.sync.contract.BudgetStatePayload? {
        val budget = state.budgetState ?: return null
        return com.sachit.moneypal.sync.contract.BudgetStatePayload(
            remainingToday = budget.remainingToday.toPlainString(),
            dailyBudget = budget.dailyBudget.toPlainString(),
            currencyCode = currencyCode,
            progressPercent = (budget.progress * 100).toInt(),
            daysRemaining = budget.daysRemaining,
            isOverBudget = budget.isOverBudget,
            updatedAtEpochMs = nowMs,
        )
    }
}
