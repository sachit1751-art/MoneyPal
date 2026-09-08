package com.sachit.moneypal.presentation.sms

import android.content.Context
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.domain.model.Transaction
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UndoSmsCaptureEntryPoint {
    fun budgetRepository(): BudgetRepository
}

object UndoSmsCaptureEntryPointAccess {
    suspend fun deleteTransaction(context: Context, transactionId: Long) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            UndoSmsCaptureEntryPoint::class.java,
        )
        val repository = entryPoint.budgetRepository()
        val transaction = repository.getTransactionById(transactionId) ?: return
        if (transaction.isRecurrent) return // never nuke a recurrent template via undo
        repository.deleteTransaction(transaction)
    }
}
