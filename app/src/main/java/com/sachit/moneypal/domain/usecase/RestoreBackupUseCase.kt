package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.data.backup.MoneyPalBackup
import com.sachit.moneypal.data.backup.RestoreResult
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.model.ArchivedBudget
import com.sachit.moneypal.domain.model.BudgetPeriod
import com.sachit.moneypal.domain.model.BudgetSettings
import com.sachit.moneypal.domain.model.BudgetSplitMode
import com.sachit.moneypal.domain.model.Category
import com.sachit.moneypal.domain.model.PaidRecurrentOccurrence
import com.sachit.moneypal.domain.model.RemainingBudgetStrategy
import com.sachit.moneypal.domain.model.SavingsPreferences
import com.sachit.moneypal.domain.model.SavingsSplitPreset
import com.sachit.moneypal.domain.model.Transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * Restores a [MoneyPalBackup] replace-upsert style (plan 010): never deletes
 * local rows, matches transactions by `clientGeneratedId` when present and by
 * (date, amount, comment) otherwise, and never overwrites a live budget.
 */
class RestoreBackupUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(backup: MoneyPalBackup): RestoreResult {
        var transactionsRestored = 0
        var transactionsSkipped = 0
        var categoriesRestored = 0
        var paidOccurrencesRestored = 0

        // Categories first so restored transactions can link to real ids by name.
        val existingByName = budgetRepository.getAllCategories().let { flow ->
            kotlinx.coroutines.flow.first(flow)
        }.associateBy { it.name }

        val categoriesToUpsert = backup.categories
            .filter { it.name.isNotBlank() }
            .mapNotNull { backupCategory ->
                val existing = existingByName[backupCategory.name]
                if (existing != null &&
                    existing.usageCount == backupCategory.usageCount &&
                    existing.isHidden == backupCategory.isHidden
                ) {
                    return@mapNotNull null // unchanged, nothing to restore
                }
                categoriesRestored++
                Category(
                    id = existing?.id ?: 0L,
                    name = backupCategory.name,
                    isHidden = backupCategory.isHidden,
                    usageCount = backupCategory.usageCount,
                    lastUsedAt = backupCategory.lastUsedAt,
                    createdAt = backupCategory.createdAt.takeIf { it > 0 }
                        ?: existing?.createdAt
                        ?: System.currentTimeMillis(),
                )
            }
        if (categoriesToUpsert.isNotEmpty()) {
            budgetRepository.upsertCategories(categoriesToUpsert)
        }

        val categoryNameToId = budgetRepository.getAllCategories().let { flow ->
            kotlinx.coroutines.flow.first(flow)
        }.associate { it.name to it.id }

        val transactionsToUpsert = mutableListOf<Transaction>()
        for (backupTransaction in backup.transactions) {
            val clientGeneratedId = backupTransaction.clientGeneratedId
            val exists = when {
                !clientGeneratedId.isNullOrBlank() ->
                    budgetRepository.existsTransactionByClientGeneratedId(clientGeneratedId)
                else -> {
                    val dateTime = LocalDateTime.ofEpochSecond(
                        backupTransaction.date / 1000, 0, ZoneOffset.UTC
                    )
                    budgetRepository.getAllTransactionsIncludingDeleted().any {
                        it.date == dateTime &&
                            it.amount.compareTo(BigDecimal(backupTransaction.amount)) == 0 &&
                            it.comment == backupTransaction.comment
                    }
                }
            }
            if (exists) {
                transactionsSkipped++
                continue
            }
            transactionsRestored++
            transactionsToUpsert += Transaction(
                id = 0L,
                amount = BigDecimal(backupTransaction.amount),
                comment = backupTransaction.comment,
                date = LocalDateTime.ofEpochSecond(
                    backupTransaction.date / 1000, 0, ZoneOffset.UTC
                ),
                createdAt = backupTransaction.createdAt,
                clientGeneratedId = clientGeneratedId,
                periodId = backupTransaction.periodId,
                isDeleted = backupTransaction.isDeleted,
                isRecurrent = backupTransaction.isRecurrent,
                recurrentFrequency = backupTransaction.recurrentFrequency?.let { name ->
                    try {
                        com.sachit.moneypal.domain.model.RecurrentFrequency.valueOf(name)
                    } catch (_: Exception) {
                        null
                    }
                },
                recurrentEndDate = backupTransaction.recurrentEndDate?.let {
                    LocalDateTime.ofEpochSecond(it / 1000, 0, ZoneOffset.UTC)
                },
                subscriptionDay = backupTransaction.subscriptionDay,
                categoryId = backupTransaction.categoryId
                    ?.let { backupTransaction.comment.takeIf { c -> c.isNotBlank() } }
                    ?.let { categoryNameToId[it] }
                    ?: backupTransaction.categoryId,
                isCredit = backupTransaction.isCredit,
                isCreditPaid = backupTransaction.isCreditPaid,
                isAdjustment = backupTransaction.isAdjustment,
                attachmentUri = backupTransaction.attachmentUri,
                originalAmount = backupTransaction.originalAmount?.let { BigDecimal(it) },
                originalCurrency = backupTransaction.originalCurrency,
            )
        }
        if (transactionsToUpsert.isNotEmpty()) {
            budgetRepository.upsertTransactions(transactionsToUpsert)
        }

        var archivedBudgetsRestored = 0
        if (backup.archivedBudgets.isNotEmpty()) {
            val archives = backup.archivedBudgets.map {
                archivedBudgetsRestored++
                ArchivedBudget(
                    periodId = it.periodId,
                    totalBudget = BigDecimal(it.totalBudget),
                    spentAmount = BigDecimal(it.spentAmount),
                    startDate = LocalDate.parse(it.startDate),
                    endDate = LocalDate.parse(it.endDate),
                    currencyCode = it.currencyCode,
                    periodType = try {
                        BudgetPeriod.valueOf(it.periodType)
                    } catch (_: Exception) {
                        BudgetPeriod.MONTHLY
                    },
                    createdAt = it.createdAt,
                )
            }
            budgetRepository.upsertArchivedBudgets(archives)
        }

        for (occurrence in backup.paidOccurrences) {
            budgetRepository.markRecurrentOccurrencePaid(
                occurrence.transactionId,
                LocalDate.ofEpochDay(occurrence.occurrenceDateEpochDay),
            )
            paidOccurrencesRestored++
        }

        // Never overwrite a live budget: only restore budget settings when the
        // local database has none (fresh install / cleared data).
        var budgetSettingsRestored = false
        val backupSettings = backup.budgetSettings
        if (backupSettings != null && budgetRepository.getBudgetSettingsSync() == null) {
            budgetRepository.saveBudgetSettings(
                BudgetSettings(
                    totalBudget = BigDecimal(backupSettings.totalBudget),
                    period = try {
                        BudgetPeriod.valueOf(backupSettings.period)
                    } catch (_: Exception) {
                        BudgetPeriod.MONTHLY
                    },
                    startDate = LocalDate.parse(backupSettings.startDate),
                    endDate = backupSettings.endDate?.let { LocalDate.parse(it) },
                    currencyCode = backupSettings.currencyCode,
                    daysInPeriod = backupSettings.daysInPeriod,
                    rollOverEnabled = backupSettings.rollOverEnabled,
                    rollOverLimit = backupSettings.rollOverLimit?.let { BigDecimal(it) },
                    rollOverCarryForward = backupSettings.rollOverCarryForward,
                    remainingBudgetStrategy = try {
                        RemainingBudgetStrategy.valueOf(backupSettings.remainingBudgetStrategy)
                    } catch (_: Exception) {
                        RemainingBudgetStrategy.ASK_ALWAYS
                    },
                    creditCardCutoffDay = backupSettings.creditCardCutoffDay,
                    splitMode = try {
                        BudgetSplitMode.valueOf(backupSettings.splitMode)
                    } catch (_: Exception) {
                        BudgetSplitMode.STATIC
                    },
                )
            )
            budgetSettingsRestored = true
        }

        var settingsRestored = false
        backup.settings?.let { backupSettings ->
            settingsRepository.setThemeMode(
                try {
                    com.sachit.moneypal.domain.model.ThemeMode.valueOf(backupSettings.themeMode)
                } catch (_: Exception) {
                    com.sachit.moneypal.domain.model.ThemeMode.SYSTEM
                }
            )
            settingsRepository.setTypographyMode(
                try {
                    com.sachit.moneypal.domain.model.TypographyMode.valueOf(backupSettings.typographyMode)
                } catch (_: Exception) {
                    com.sachit.moneypal.domain.model.TypographyMode.EXPRESSIVE
                }
            )
            settingsRepository.setContrastMode(
                try {
                    com.sachit.moneypal.domain.model.ContrastMode.valueOf(backupSettings.contrastMode)
                } catch (_: Exception) {
                    com.sachit.moneypal.domain.model.ContrastMode.NORMAL
                }
            )
            settingsRepository.setAppColorScheme(
                try {
                    com.sachit.moneypal.domain.model.AppColorScheme.valueOf(backupSettings.colorScheme)
                } catch (_: Exception) {
                    com.sachit.moneypal.domain.model.AppColorScheme.BRAND
                }
            )
            settingsRepository.setDynamicColorEnabled(backupSettings.dynamicColorEnabled)
            settingsRepository.setRoundedFontEnabled(backupSettings.roundedFontEnabled)
            settingsRepository.setAmoledEnabled(backupSettings.amoledEnabled)
            settingsRepository.setLanguage(backupSettings.language)
            settingsRepository.setNotificationTime(
                backupSettings.notificationHour, backupSettings.notificationMinute
            )
            settingsRepository.setRecurrentNotificationTime(
                backupSettings.recurrentNotificationHour, backupSettings.recurrentNotificationMinute
            )
            settingsRepository.setSavingsPreferences(
                SavingsPreferences(
                    preset = try {
                        SavingsSplitPreset.valueOf(backupSettings.savingsPreset)
                    } catch (_: Exception) {
                        SavingsSplitPreset.BALANCED
                    },
                    needsPct = backupSettings.savingsNeedsPct,
                    wantsPct = backupSettings.savingsWantsPct,
                    savingsPct = backupSettings.savingsSavingsPct,
                    savingsGoalAmount = backupSettings.savingsGoalAmount?.let { BigDecimal(it) },
                    savingsGoalMonths = backupSettings.savingsGoalMonths,
                )
            )
            settingsRestored = true
        }

        return RestoreResult(
            transactionsRestored = transactionsRestored,
            transactionsSkipped = transactionsSkipped,
            categoriesRestored = categoriesRestored,
            archivedBudgetsRestored = archivedBudgetsRestored,
            paidOccurrencesRestored = paidOccurrencesRestored,
            budgetSettingsRestored = budgetSettingsRestored,
            settingsRestored = settingsRestored,
        )
    }
}
