package com.sachit.moneypal.wear.sync

interface SyncRepository {
    suspend fun syncPendingExpenses(): Boolean
}
