package com.sachit.moneypal.domain.datahealth

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the [DataHealthScanner] needs for one read-only pass (plan 049).
 * The data layer fills this from count-only DAO queries plus a file-existence
 * check; the scanner itself stays free of Android and Room dependencies.
 */
data class DataHealthScanInput(
    val totalTransactions: Int,
    val incomeCount: Int,
    val smsCapturedCount: Int,
    val smsConfidences: List<Int>,
    val attachmentPaths: List<String>,
    val existingAttachmentPaths: Set<String>,
    val duplicateClientGeneratedIdCount: Int,
    val oldestDateMillis: Long?,
)

/**
 * Read-only snapshot of the user's data health (plan 049). Issue counters
 * (missing attachments, duplicate client ids) drive the amber "review" rows
 * in the dashboard; everything else is informational.
 */
data class DataHealthReport(
    val totalTransactions: Int,
    val incomeCount: Int,
    val expenseCount: Int,
    val smsCapturedCount: Int,
    val medianSmsConfidence: BigDecimal?,
    val attachmentCount: Int,
    val missingAttachmentCount: Int,
    val duplicateClientGeneratedIdCount: Int,
    val oldestTransactionDate: LocalDate?,
) {
    /** True when at least one issue counter is non-zero. */
    val hasIssues: Boolean
        get() = missingAttachmentCount > 0 || duplicateClientGeneratedIdCount > 0
}

/**
 * Pure aggregator behind the data health dashboard (plan 049): turns raw
 * count-only scan results into a [DataHealthReport]. Never touches data —
 * the whole dashboard is read-only by design.
 */
class DataHealthScanner @javax.inject.Inject constructor() {

    fun scan(input: DataHealthScanInput): DataHealthReport {
        val missingAttachments = input.attachmentPaths.count { path ->
            path !in input.existingAttachmentPaths
        }

        return DataHealthReport(
            totalTransactions = input.totalTransactions,
            incomeCount = input.incomeCount,
            expenseCount = (input.totalTransactions - input.incomeCount).coerceAtLeast(0),
            smsCapturedCount = input.smsCapturedCount,
            medianSmsConfidence = median(input.smsConfidences),
            attachmentCount = input.attachmentPaths.size,
            missingAttachmentCount = missingAttachments,
            duplicateClientGeneratedIdCount = input.duplicateClientGeneratedIdCount,
            oldestTransactionDate = input.oldestDateMillis?.let { millis ->
                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            },
        )
    }

    /**
     * Median confidence on a 0–100 scale: HALF_UP to 1 decimal, null for an
     * empty set. Even counts average the two middle values.
     */
    internal fun median(values: List<Int>): BigDecimal? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return when {
            sorted.size % 2 == 1 -> BigDecimal(sorted[mid])
            else -> BigDecimal(sorted[mid - 1] + sorted[mid])
                .divide(BigDecimal(2), 1, RoundingMode.HALF_UP)
        }
    }
}
