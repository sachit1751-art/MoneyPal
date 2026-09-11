package com.sachit.moneypal.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Versioned, lossless local backup of all MoneyPal data (plan 010).
 *
 * Money amounts are plain strings (money never floats) and dates are ISO-8601.
 * `schemaVersion` gates forward compatibility: `BackupCodec` decodes with
 * `ignoreUnknownKeys = true` and refuses unknown schema versions, so v1 files
 * keep restoring into newer schemas.
 */
@Serializable
data class MoneyPalBackup(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerialName("exportedAtEpochMs") val exportedAtEpochMs: Long,
    val transactions: List<BackupTransaction> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val archivedBudgets: List<BackupArchivedBudget> = emptyList(),
    val paidOccurrences: List<BackupPaidOccurrence> = emptyList(),
    val budgetSettings: BackupBudgetSettings? = null,
    val settings: BackupSettings? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupTransaction(
    val id: Long = 0,
    val amount: String,
    val comment: String = "",
    /** Epoch millis UTC (Room storage format). */
    val date: Long,
    val createdAt: Long = 0,
    val clientGeneratedId: String? = null,
    val periodId: Long = 0,
    val isDeleted: Boolean = false,
    val isRecurrent: Boolean = false,
    val recurrentFrequency: String? = null,
    val recurrentEndDate: Long? = null,
    val subscriptionDay: Int? = null,
    val categoryId: Long? = null,
    val isCredit: Boolean = false,
    val isCreditPaid: Boolean = false,
    val isAdjustment: Boolean = false,
    val attachmentUri: String? = null,
    val originalAmount: String? = null,
    val originalCurrency: String? = null,
)

@Serializable
data class BackupCategory(
    val name: String,
    val isHidden: Boolean = false,
    val usageCount: Int = 0,
    val lastUsedAt: Long? = null,
    val createdAt: Long = 0,
)

@Serializable
data class BackupArchivedBudget(
    val periodId: Long,
    val totalBudget: String,
    val spentAmount: String,
    val startDate: String,
    val endDate: String,
    val currencyCode: String,
    val periodType: String,
    val createdAt: Long = 0,
)

@Serializable
data class BackupPaidOccurrence(
    val transactionId: Long,
    /** Epoch day of the occurrence date. */
    val occurrenceDateEpochDay: Long,
)

@Serializable
data class BackupBudgetSettings(
    val totalBudget: String,
    val period: String,
    val startDate: String,
    val endDate: String? = null,
    val currencyCode: String = "USD",
    val daysInPeriod: Int = 1,
    val rollOverEnabled: Boolean = false,
    val rollOverLimit: String? = null,
    val rollOverCarryForward: Boolean = false,
    val remainingBudgetStrategy: String = "ASK_ALWAYS",
    val creditCardCutoffDay: Int? = null,
    val splitMode: String = "STATIC",
)

/**
 * Only the restore-safe subset of UserSettings (plan 010): theme/language/
 * notification times/savings. Session-state keys (current period, rollover
 * state, SMS seen markers, tutorial progress) are deliberately excluded —
 * restoring those can corrupt period math.
 */
@Serializable
data class BackupSettings(
    val themeMode: String = "SYSTEM",
    val typographyMode: String = "EXPRESSIVE",
    val contrastMode: String = "NORMAL",
    val colorScheme: String = "BRAND",
    val dynamicColorEnabled: Boolean = false,
    val roundedFontEnabled: Boolean = true,
    val amoledEnabled: Boolean = false,
    val language: String = "en",
    val notificationHour: Int = 9,
    val notificationMinute: Int = 0,
    val recurrentNotificationHour: Int = 8,
    val recurrentNotificationMinute: Int = 0,
    val savingsPreset: String = "BALANCED",
    val savingsNeedsPct: Int = 50,
    val savingsWantsPct: Int = 30,
    val savingsSavingsPct: Int = 20,
    val savingsGoalAmount: String? = null,
    val savingsGoalMonths: Int? = null,
)

/** Thrown when a backup file is corrupt or from an incompatible schema. */
class BackupFormatException(reason: String) : Exception(reason)

/** Counts reported to the UI after a restore completes. */
data class RestoreResult(
    val transactionsRestored: Int,
    val transactionsSkipped: Int,
    val categoriesRestored: Int,
    val archivedBudgetsRestored: Int,
    val paidOccurrencesRestored: Int,
    val budgetSettingsRestored: Boolean,
    val settingsRestored: Boolean,
)

/** Converts ISO-8601 dates without pulling in kotlinx-datetime. */
internal object BackupDates {
    fun toEpochMillis(date: LocalDateTime): Long =
        date.toEpochSecond(ZoneOffset.UTC) * 1000

    fun fromEpochMillis(millis: Long): LocalDateTime =
        LocalDateTime.ofEpochSecond(millis / 1000, 0, ZoneOffset.UTC)

    fun localDateToMillis(date: LocalDate): Long = date.toEpochDay() * 86400000

    fun localDateFromMillis(millis: Long): LocalDate = LocalDate.ofEpochDay(millis / 86400000)

    fun nowEpochMs(): Long = Instant.now().toEpochMilli()
}

/** String form of a BigDecimal for backup storage. */
internal fun BigDecimal?.backupString(): String? = this?.toPlainString()
