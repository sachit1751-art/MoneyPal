package com.sachit.moneypal.presentation.ui.settings.backup

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupTransferEntryPoint {
    fun backupTransferManager(): BackupTransferManager
}
