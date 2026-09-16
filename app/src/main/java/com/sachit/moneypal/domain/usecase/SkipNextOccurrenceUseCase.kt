package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.domain.calculator.RecurringExpenseCalculator
import com.sachit.moneypal.domain.model.Transaction
import java.time.LocalDate
import javax.inject.Inject

/**
 * Marks the next occurrence of a recurring expense as skipped (plan 008): the
 * occurrence will not be billed, counted in due-today sums, or notified. The
 * schedule itself is unchanged — the following occurrence fires normally.
 */
class SkipNextOccurrenceUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
) {
    /**
     * @return true when an occurrence was found and skipped.
     */
    suspend operator fun invoke(transaction: Transaction, today: LocalDate): Boolean {
        val stableId = transaction.sourceTransactionId ?: transaction.id
        val template = budgetRepository.getTransactionById(stableId) ?: transaction
        val next = RecurringExpenseCalculator().nextOccurrenceDate(template, today) ?: return false
        budgetRepository.markOccurrenceSkipped(stableId, next)
        return true
    }
}
