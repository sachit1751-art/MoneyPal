package com.sachit.moneypal.presentation.ui.history.edit

import com.sachit.moneypal.data.attachments.AttachmentStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Composition-local-free access point for [AttachmentStore] inside
 * [TransactionEditScreen], which is hosted inside dialogs that don't own a
 * ViewModel scoping the store.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AttachmentEntryPoint {
    fun attachmentStore(): AttachmentStore
}
