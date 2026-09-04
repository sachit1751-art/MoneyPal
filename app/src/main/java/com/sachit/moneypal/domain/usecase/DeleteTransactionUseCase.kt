package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.domain.model.Transaction
import javax.inject.Inject

class DeleteTransactionUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository
) {
    suspend operator fun invoke(transaction: Transaction) {
        budgetRepository.deleteTransaction(transaction)
    }
}
