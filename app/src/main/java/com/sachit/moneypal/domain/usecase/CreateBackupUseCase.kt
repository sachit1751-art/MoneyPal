package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.data.backup.BackupArchivedBudget
import com.sachit.moneypal.data.backup.BackupBudgetSettings
import com.sachit.moneypal.data.backup.BackupCategory
import com.sachit.moneypal.data.backup.BackupPaidOccurrence
import com.sachit.moneypal.data.backup.BackupSettings
import com.sachit.moneypal.data.backup.BackupTransaction
import com.sachit.moneypal.data.backup.MoneyPalBackup
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.model.ArchivedBudget
import com.sachit.moneypal.domain.model.BudgetPeriod
import com.sachit.moneypal.domain.model.BudgetSettings
import com.sachit.moneypal.domain.model.BudgetSplitMode
import com.sachit.moneypal.domain.model.Category
import com.sachit.moneypal.domain.model.RemainingBudgetStrategy
import com.sachit.moneypal.domain.model.SavingsPreferences
import com.sachit.moneypal.domain.model.SavingsSplitPreset
import com.sachit.moneypal.domain.model.ThemeMode
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.domain.model.TypographyMode
import com.sachit.moneypal.domain.model.ContrastMode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * Builds a full, versioned local backup of all MoneyPal data (plan 010).
 * Includes transactions (with deleted flags), categories with usage counts,
 * archived budgets, paid recurrent occurrences, budget settings, and the
 * restore-safe subset of user settings.
 */
class CreateBackupUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): MoneyPalBackup {
        val transactions = budgetRepository.getAllTransactionsIncludingDeleted()
        val categories = budgetRepository.getAllCategories().let { flow ->
            kotlinx.coroutines.flow.first(flow)
        }
        val archives = budgetRepository.getArchivedBudgets().let { flow ->
            kotlinx.coroutines.flow.first(flow)
        }
        val paidOccurrences = budgetRepository.getPaidRecurrentOccurrences().let { flow ->
            kotlinx.coroutines.flow.first(flow)
        }
        val budgetSettings = budgetRepository.getBudgetSettingsSync()
        val settings = settingsRepository.getSettings()

        return MoneyPalBackup(
            exportedAtEpochMs = System.currentTimeMillis(),
            transactions = transactions.map { it.toBackup() },
            categories = categories.map { it.toBackup() },
            archivedBudgets = archives.map { it.toBackup() },
            paidOccurrences = paidOccurrences.map {
                BackupPaidOccurrence(
                    transactionId = it.transactionId,
                    occurrenceDateEpochDay = it.occurrenceDate.toEpochDay(),
                )
            },
            budgetSettings = budgetSettings?.toBackup(),
            settings = settings.toBackup(),
        )
    }
}

internal fun Transaction.toBackup(): BackupTransaction = BackupTransaction(
    id = id,
    amount = amount.toPlainString(),
    comment = comment,
    date = (date?.toEpochSecond(ZoneOffset.UTC) ?: 0L) * 1000,
    createdAt = createdAt,
    clientGeneratedId = clientGeneratedId,
    periodId = periodId,
    isDeleted = isDeleted,
    isRecurrent = isRecurrent,
    recurrentFrequency = recurrentFrequency?.name,
    recurrentEndDate = recurrentEndDate?.toEpochSecond(ZoneOffset.UTC)?.times(1000),
    subscriptionDay = subscriptionDay,
    categoryId = categoryId,
    isCredit = isCredit,
    isCreditPaid = isCreditPaid,
    isAdjustment = isAdjustment,
    attachmentUri = attachmentUri,
    originalAmount = originalAmount?.toPlainString(),
    originalCurrency = originalCurrency,
)

internal fun Category.toBackup(): BackupCategory = BackupCategory(
    name = name,
    isHidden = isHidden,
    usageCount = usageCount,
    lastUsedAt = lastUsedAt,
    createdAt = createdAt,
)

internal fun ArchivedBudget.toBackup(): BackupArchivedBudget = BackupArchivedBudget(
    periodId = periodId,
    totalBudget = totalBudget.toPlainString(),
    spentAmount = spentAmount.toPlainString(),
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    currencyCode = currencyCode,
    periodType = periodType.name,
    createdAt = createdAt,
)

internal fun BudgetSettings.toBackup(): BackupBudgetSettings = BackupBudgetSettings(
    totalBudget = totalBudget.toPlainString(),
    period = period.name,
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    currencyCode = currencyCode,
    daysInPeriod = daysInPeriod,
    rollOverEnabled = rollOverEnabled,
    rollOverLimit = rollOverLimit?.toPlainString(),
    rollOverCarryForward = rollOverCarryForward,
    remainingBudgetStrategy = remainingBudgetStrategy.name,
    creditCardCutoffDay = creditCardCutoffDay,
    splitMode = splitMode.name,
)

internal fun com.sachit.moneypal.domain.model.UserSettings.toBackup(): BackupSettings =
    BackupSettings(
        themeMode = themeMode.name,
        typographyMode = typographyMode.name,
        contrastMode = contrastMode.name,
        colorScheme = colorScheme.name,
        dynamicColorEnabled = dynamicColorEnabled,
        roundedFontEnabled = isRoundedFontEnabled,
        amoledEnabled = isAmoledEnabled,
        language = language,
        notificationHour = notificationHour,
        notificationMinute = notificationMinute,
        recurrentNotificationHour = recurrentNotificationHour,
        recurrentNotificationMinute = recurrentNotificationMinute,
        savingsPreset = savingsPreferences.preset.name,
        savingsNeedsPct = savingsPreferences.needsPct,
        savingsWantsPct = savingsPreferences.wantsPct,
        savingsSavingsPct = savingsPreferences.savingsPct,
        savingsGoalAmount = savingsPreferences.savingsGoalAmount?.toPlainString(),
        savingsGoalMonths = savingsPreferences.savingsGoalMonths,
    )
