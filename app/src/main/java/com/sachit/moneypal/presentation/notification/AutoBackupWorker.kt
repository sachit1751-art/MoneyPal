package com.sachit.moneypal.presentation.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sachit.moneypal.data.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import logcat.logcat

/**
 * Writes the full JSON backup into the user-chosen SAF folder when due
 * (plan 004): runs at most once per [AutoBackupScheduler.CADENCE_DAYS].
 */
class AutoBackupWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "auto_backup"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AutoBackupEntryPoint {
        fun autoBackupScheduler(): AutoBackupScheduler
    }

    override suspend fun doWork(): Result {
        val scheduler = EntryPointAccessors.fromApplication(
            applicationContext,
            AutoBackupEntryPoint::class.java,
        ).autoBackupScheduler()
        return if (scheduler.runNow()) Result.success() else Result.retry()
    }
}
