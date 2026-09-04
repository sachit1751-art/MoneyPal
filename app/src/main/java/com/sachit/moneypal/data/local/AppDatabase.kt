package com.sachit.moneypal.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.sachit.moneypal.data.local.dao.ArchivedBudgetDao
import com.sachit.moneypal.data.local.dao.BudgetSettingsDao
import com.sachit.moneypal.data.local.dao.CategoryDao
import com.sachit.moneypal.data.local.dao.PaidRecurrentOccurrenceDao
import com.sachit.moneypal.data.local.dao.QueuedTransactionDao
import com.sachit.moneypal.data.local.dao.TransactionDao
import com.sachit.moneypal.data.local.entity.ArchivedBudgetEntity
import com.sachit.moneypal.data.local.entity.BudgetSettingsEntity
import com.sachit.moneypal.data.local.entity.CategoryEntity
import com.sachit.moneypal.data.local.entity.PaidRecurrentOccurrenceEntity
import com.sachit.moneypal.data.local.entity.QueuedTransactionEntity
import com.sachit.moneypal.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        BudgetSettingsEntity::class,
        CategoryEntity::class,
        QueuedTransactionEntity::class,
        ArchivedBudgetEntity::class,
        PaidRecurrentOccurrenceEntity::class
    ],
    version = 17,
    autoMigrations = [
        AutoMigration(from = 16, to = 17)
    ],
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun budgetSettingsDao(): BudgetSettingsDao

    abstract fun archivedBudgetDao(): ArchivedBudgetDao

    abstract fun categoryDao(): CategoryDao

    abstract fun queuedTransactionDao(): QueuedTransactionDao

    abstract fun paidRecurrentOccurrenceDao(): PaidRecurrentOccurrenceDao

    companion object {
        const val DATABASE_NAME = "minus_budget.db"
    }
}
