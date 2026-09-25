package com.sachit.moneypal.data.repository

import com.sachit.moneypal.data.attachments.AttachmentStore
import com.sachit.moneypal.data.local.dao.TransactionDao
import com.sachit.moneypal.domain.datahealth.DataHealthReport
import com.sachit.moneypal.domain.datahealth.DataHealthScanInput
import com.sachit.moneypal.domain.datahealth.DataHealthScanner
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only data health scan (plan 049): count-only DAO queries plus a file
 * existence check per stored receipt path. Raw results feed the pure
 * [DataHealthScanner]; this class never mutates data.
 */
@Singleton
class DataHealthRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val attachmentStore: AttachmentStore,
    private val scanner: DataHealthScanner,
) {

    suspend fun scan(): DataHealthReport {
        val attachmentPaths = transactionDao.attachmentUris()
        val receiptsDir = attachmentStore.attachmentsDir().absolutePath
        val existingPaths = attachmentPaths
            .filter { it.startsWith(receiptsDir) }
            .filter { File(it).exists() }
            .toSet()

        return scanner.scan(
            DataHealthScanInput(
                totalTransactions = transactionDao.countActive(),
                incomeCount = transactionDao.countIncome(),
                smsCapturedCount = transactionDao.countSmsCaptured(),
                smsConfidences = transactionDao.smsConfidences(),
                attachmentPaths = attachmentPaths,
                existingAttachmentPaths = existingPaths,
                duplicateClientGeneratedIdCount =
                    transactionDao.countDuplicateClientGeneratedIds(),
                oldestDateMillis = transactionDao.oldestDateMillis(),
            ),
        )
    }
}
