package com.sachit.moneypal.presentation.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sachit.moneypal.R
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.calculator.RecurringExpenseCalculator
import com.sachit.moneypal.domain.calculator.RecurringLinker
import com.sachit.moneypal.domain.model.BudgetSettings
import com.sachit.moneypal.domain.model.Category
import com.sachit.moneypal.domain.model.PaidRecurrentOccurrence
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.domain.model.UserSettings
import com.sachit.moneypal.domain.usecase.GetCurrentPeriodIdUseCase
import com.sachit.moneypal.domain.usecase.ObserveCurrentPeriodBoundaryUseCase
import com.sachit.moneypal.domain.usecase.PersistBudgetSettingsUseCase
import com.sachit.moneypal.domain.usecase.SkipNextOccurrenceUseCase
import com.sachit.moneypal.presentation.ui.budget.BudgetStateCalculator
import com.sachit.moneypal.presentation.ui.budget.BudgetTransactionHandler
import com.sachit.moneypal.presentation.ui.theme.component.expense.UpcomingRecurrentItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "HistoryViewModel"

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val budgetTransactionHandler: BudgetTransactionHandler,
    private val settingsRepository: SettingsRepository,
    private val budgetStateCalculator: BudgetStateCalculator,
    private val observeCurrentPeriodBoundaryUseCase: ObserveCurrentPeriodBoundaryUseCase,
    private val persistBudgetSettingsUseCase: PersistBudgetSettingsUseCase,
    private val getCurrentPeriodIdUseCase: GetCurrentPeriodIdUseCase,
    private val skipNextOccurrenceUseCase: SkipNextOccurrenceUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _expandedDates = MutableStateFlow(emptySet<LocalDate>())
    private val _editingTransaction = MutableStateFlow<Transaction?>(null)
    private val _recurrentToDelete = MutableStateFlow<Transaction?>(null)
    private val _recurrentToEdit = MutableStateFlow<Transaction?>(null)
    private val _showDeleteRecurrentDialog = MutableStateFlow(false)
    private val _showPastPeriod = MutableStateFlow(false)
    private val _showOutOfPeriodSubscriptions = MutableStateFlow(false)
    private val _showUpcomingRecurrentInPeriod = MutableStateFlow(true)
    private val _lockSwipeable = MutableStateFlow(true)
    private val _expandedTransactionId = MutableStateFlow<Long?>(null)
    private val _pendingRemovedTransactions = MutableStateFlow(emptyMap<Long, Transaction>())
    private val _filter = MutableStateFlow(HistoryFilterState())

    /** Plan 015: low-confidence SMS captures + dialog visibility. */
    val smsReviewCandidates: StateFlow<List<Transaction>> = budgetTransactionHandler.budgetRepository
        .observeSmsReviewCandidates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    private val _showSmsReviewDialog = MutableStateFlow(false)

    /** Plan 017: expenses awaiting their refund. */
    val pendingRefunds: StateFlow<List<Transaction>> = budgetTransactionHandler.budgetRepository
        .observePendingRefunds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _effects = MutableSharedFlow<HistoryUiEffect>()
    val effects: SharedFlow<HistoryUiEffect> = _effects.asSharedFlow()

    private var autoDismissJob: Job? = null

    private val uiInputs = combine(
        listOf(
            _expandedDates,
            _editingTransaction,
            _recurrentToDelete,
            _recurrentToEdit,
            _showDeleteRecurrentDialog,
            _showPastPeriod,
            _showOutOfPeriodSubscriptions,
            _showUpcomingRecurrentInPeriod,
            _lockSwipeable,
            _expandedTransactionId,
            _pendingRemovedTransactions,
            _filter,
            _showSmsReviewDialog,
            smsReviewCandidates,
            pendingRefunds,
        )
    ) { array ->
        UIInputs(
            expandedDates = array[0] as Set<LocalDate>,
            editingTransaction = array[1] as Transaction?,
            recurrentToDelete = array[2] as Transaction?,
            recurrentToEdit = array[3] as Transaction?,
            showDeleteRecurrentDialog = array[4] as Boolean,
            showPastPeriod = array[5] as Boolean,
            showOutOfPeriodSubscriptions = array[6] as Boolean,
            showUpcomingRecurrentInPeriod = array[7] as Boolean,
            lockSwipeable = array[8] as Boolean,
            expandedTransactionId = array[9] as Long?,
            pendingRemovedTransactions = array[10] as Map<Long, Transaction>,
            filter = array[11] as HistoryFilterState,
            showSmsReviewDialog = array[12] as Boolean,
            smsReviewCandidates = array[13] as List<Transaction>,
            pendingRefunds = array[14] as List<Transaction>,
        )
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        budgetTransactionHandler.budgetRepository.getTransactions(),
        budgetTransactionHandler.budgetRepository.getBudgetSettings(),
        observeCurrentPeriodBoundaryUseCase(),
        settingsRepository.observeSettings(),
        budgetTransactionHandler.budgetRepository.getActiveCategories(),
        budgetTransactionHandler.budgetRepository.getPaidRecurrentOccurrences(),
        uiInputs
    ) { array ->
        val transactions = array[0] as List<Transaction>
        val budgetSettings = array[1] as BudgetSettings?
        @Suppress("UNCHECKED_CAST")
        val periodBoundary = array[2] as Pair<Long, Long>
        val userSettings = array[3] as UserSettings?
        @Suppress("UNCHECKED_CAST")
        val categories = array[4] as List<Category>
        @Suppress("UNCHECKED_CAST")
        val paidOccurrences = array[5] as Set<PaidRecurrentOccurrence>
        val inputs = array[6] as UIInputs

        calculateHistoryUiState(
            transactions = transactions,
            budgetSettings = budgetSettings,
            currentPeriodStartedAtMillis = periodBoundary.first,
            currentPeriodId = periodBoundary.second,
            userSettings = userSettings,
            categories = categories,
            paidOccurrences = paidOccurrences,
            inputs = inputs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HistoryUiState()
    )

    fun processIntent(intent: HistoryUiIntent) {
        when (intent) {
            is HistoryUiIntent.ToggleExpandedDate -> toggleExpandedDate(intent.date)
            is HistoryUiIntent.SetEditingTransaction -> _editingTransaction.value = intent.transaction
            is HistoryUiIntent.SetRecurrentToDelete -> {
                _recurrentToDelete.value = intent.transaction
                _showDeleteRecurrentDialog.value = intent.transaction != null
            }

            is HistoryUiIntent.SetRecurrentToEdit -> _recurrentToEdit.value = intent.transaction
            is HistoryUiIntent.DismissDeleteRecurrentDialog -> {
                _recurrentToDelete.value = null
                _showDeleteRecurrentDialog.value = false
            }

            is HistoryUiIntent.TogglePastPeriod -> _showPastPeriod.value = intent.visible
            is HistoryUiIntent.ToggleOutOfPeriodSubscriptions -> _showOutOfPeriodSubscriptions.value = intent.visible
            is HistoryUiIntent.ToggleUpcomingRecurrentInPeriod -> _showUpcomingRecurrentInPeriod.value = intent.visible
            is HistoryUiIntent.DeleteTransaction -> deleteTransaction(intent.transaction)
            is HistoryUiIntent.SaveEditedTransaction -> saveEditedTransaction(intent.transaction)
            is HistoryUiIntent.ConfirmDeleteRecurrent -> confirmDeleteRecurrent(intent.transaction)
            is HistoryUiIntent.MarkTransactionAsPaid -> markTransactionAsPaid(intent.transaction)
            is HistoryUiIntent.SkipNextOccurrence -> skipNextOccurrence(intent.transaction)
            is HistoryUiIntent.CloneTransaction -> cloneTransaction(intent.transaction)
            is HistoryUiIntent.ToggleRefundExpected -> toggleRefundExpected(intent.transaction)
            is HistoryUiIntent.MarkRefunded -> markRefunded(intent.transaction)
            is HistoryUiIntent.RefundReceived -> onRefundReceived(intent.transaction)
            is HistoryUiIntent.SetLockSwipeable -> _lockSwipeable.value = intent.locked
            is HistoryUiIntent.ToggleExpandedTransaction -> toggleExpandedTransaction(intent.transactionId)
            is HistoryUiIntent.UpdateCreditCutoffDay -> updateCreditCutoffDay(intent.day)
            is HistoryUiIntent.SetSmsReviewDialogVisible -> _showSmsReviewDialog.value = true
            is HistoryUiIntent.DismissSmsReviewDialog -> _showSmsReviewDialog.value = false
            is HistoryUiIntent.ConfirmSmsCapture -> confirmSmsCapture(intent.transaction)
            is HistoryFilterIntent.SetSearchQuery -> _filter.update { it.copy(query = intent.query) }
            is HistoryFilterIntent.ToggleCategoryName -> _filter.update {
                if (it.categoryName == intent.name) {
                    it.copy(categoryName = null)
                } else {
                    it.copy(categoryName = intent.name)
                }
            }
            is HistoryFilterIntent.SetAmountFilter -> _filter.update { it.copy(minAmount = intent.min, maxAmount = intent.max) }
            is HistoryFilterIntent.ToggleRecurrentOnly -> _filter.update { it.copy(recurrentOnly = intent.enabled) }
            is HistoryFilterIntent.ToggleCreditOnly -> _filter.update { it.copy(creditOnly = intent.enabled) }
            is HistoryFilterIntent.SetPaymentMethodFilter -> _filter.update {
                if (it.paymentMethod == intent.method) {
                    it.copy(paymentMethod = null)
                } else {
                    it.copy(paymentMethod = intent.method)
                }
            }
            is HistoryFilterIntent.ClearFilters -> _filter.value = HistoryFilterState()
        }
    }

    /** Plan 015: user confirmed a low-confidence SMS capture — trust it. */
    private fun confirmSmsCapture(transaction: Transaction) {
        viewModelScope.launch {
            runCatching { budgetTransactionHandler.budgetRepository.confirmSmsCapture(transaction.id) }
                .onFailure { e ->
                    logcat(TAG) { "confirmSmsCapture failed for id=${transaction.id}: $e" }
                    _effects.emit(
                        HistoryUiEffect.ShowSnackbar(
                            context.getString(R.string.sms_review_confirm_failed)
                        )
                    )
                }
        }
    }

    private fun toggleExpandedTransaction(id: Long?) {
        _expandedTransactionId.update { currentId ->
            if (currentId == id) null else id
        }
    }

    private fun updateCreditCutoffDay(day: Int) {
        val currentSettings = uiState.value.budgetSettings ?: return
        if (day !in 1..31) return

        viewModelScope.launch {
            persistBudgetSettingsUseCase(
                settings = currentSettings.copy(creditCardCutoffDay = day),
                forceNewPeriodBoundary = false,
            )
        }
    }

    private fun deleteTransaction(transaction: Transaction) {
        autoDismissJob?.cancel()
        _pendingRemovedTransactions.update { it + (transaction.id to transaction) }
        autoDismissJob = viewModelScope.launch {
            delay(EXIT_ANIMATION_DURATION_MS)
            val result = budgetTransactionHandler.deleteTransaction(transaction)
            _pendingRemovedTransactions.update { it - transaction.id }
            if (result.isFailure) {
                logcat(TAG) { "deleteTransaction failed for id=${transaction.id}: ${result.exceptionOrNull()}" }
                _effects.emit(
                    HistoryUiEffect.ShowSnackbar(
                        context.getString(R.string.history_snackbar_delete_transaction_failed)
                    )
                )
            }
        }
    }

    private fun saveEditedTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val success = budgetTransactionHandler.editTransaction(transaction)
            if (success) {
                _editingTransaction.value = null
            } else {
                _effects.emit(
                    HistoryUiEffect.ShowSnackbar(
                        context.getString(R.string.history_snackbar_save_transaction_failed)
                    )
                )
            }
        }
    }

    private fun confirmDeleteRecurrent(transaction: Transaction) {
        viewModelScope.launch {
            _recurrentToDelete.value = null
            _showDeleteRecurrentDialog.value = false
            val result = budgetTransactionHandler.deleteTransaction(transaction)
            if (result.isFailure) {
                logcat(TAG) { "confirmDeleteRecurrent failed for id=${transaction.id}: ${result.exceptionOrNull()}" }
                _effects.emit(
                    HistoryUiEffect.ShowSnackbar(
                        context.getString(R.string.history_snackbar_delete_recurrent_failed)
                    )
                )
            }
        }
    }

    private fun markTransactionAsPaid(transaction: Transaction) {
        viewModelScope.launch {
            val activePeriodId = getCurrentPeriodIdUseCase().takeIf { it > 0L }
                ?: uiState.value.currentPeriodId
            val result = budgetTransactionHandler.markRecurrentOccurrencePaid(transaction, activePeriodId)
            if (result.isFailure) {
                _effects.emit(
                    HistoryUiEffect.ShowSnackbar(
                        context.getString(R.string.history_snackbar_mark_paid_failed)
                    )
                )
            }
        }
    }

    /** Skips the next occurrence of a recurring expense (plan 008). */
    private fun skipNextOccurrence(transaction: Transaction) {
        viewModelScope.launch {
            val skipped = skipNextOccurrenceUseCase(transaction, LocalDate.now())
            _effects.emit(
                HistoryUiEffect.ShowSnackbar(
                    context.getString(
                        if (skipped) R.string.recurrent_skipped_snackbar
                        else R.string.history_snackbar_mark_paid_failed
                    )
                )
            )
        }
    }

    /** Duplicates [transaction] with a fresh id, today's date and a clean clientGeneratedId. */
    private fun cloneTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val clone = transaction.copy(
                id = 0L,
                createdAt = System.currentTimeMillis(),
                clientGeneratedId = null,
                date = java.time.LocalDateTime.now(),
                isCreditPaid = false,
            )
            budgetTransactionHandler.budgetRepository.addTransaction(clone)
        }
    }

    private fun toggleRefundExpected(transaction: Transaction) {
        viewModelScope.launch {
            if (transaction.refundExpected) {
                budgetTransactionHandler.budgetRepository.setRefundExpected(transaction.id, false)
            } else {
                budgetTransactionHandler.budgetRepository.setRefundExpected(transaction.id, true)
            }
        }
    }

    private fun markRefunded(transaction: Transaction) {
        viewModelScope.launch {
            budgetTransactionHandler.budgetRepository.markRefunded(transaction.id)
        }
    }

    /**
     * Plan 017: user tapped "Received" on a pending refund — credit the
     * budget and settle the original row.
     */
    private fun onRefundReceived(transaction: Transaction) {
        viewModelScope.launch {
            val result = budgetTransactionHandler.creditRefund(
                original = transaction,
                commentPrefix = context.getString(R.string.refund_comment_prefix),
            )
            if (result.isFailure) {
                logcat(TAG) { "onRefundReceived failed for id=${transaction.id}: ${result.exceptionOrNull()}" }
                _effects.emit(
                    HistoryUiEffect.ShowSnackbar(
                        context.getString(R.string.refund_received_failed)
                    )
                )
            }
        }
    }

    private fun toggleExpandedDate(date: LocalDate) {
        _expandedDates.update { expanded ->
            val currentExpanded = if (expanded.isEmpty()) {
                uiState.value.expandedDates
            } else {
                expanded
            }
            if (currentExpanded.contains(date)) currentExpanded - date else currentExpanded + date
        }
    }

    private fun calculateHistoryUiState(
        transactions: List<Transaction>,
        budgetSettings: BudgetSettings?,
        currentPeriodStartedAtMillis: Long,
        currentPeriodId: Long,
        userSettings: UserSettings?,
        categories: List<Category>,
        paidOccurrences: Set<PaidRecurrentOccurrence>,
        inputs: UIInputs
    ): HistoryUiState {
        val displayTx = buildDisplayTransactions(transactions, inputs.pendingRemovedTransactions)

        val startDate = budgetSettings?.startDate ?: LocalDate.now().minusDays(30)
        val endDate = budgetSettings?.getPeriodEndDate() ?: LocalDate.now()
        val today = LocalDate.now()
        val previousPeriodId = currentPeriodId - 1

        val (currentPeriodTx, pastPeriodTx) = splitPeriodTransactions(
            transactions = displayTx,
            budgetStartDate = startDate,
            budgetEndDate = endDate,
            currentPeriodStartedAtMillis = currentPeriodStartedAtMillis,
            currentPeriodId = currentPeriodId,
            previousPeriodId = previousPeriodId,
        )

        val budgetState = budgetSettings?.let { s ->
            val periodTransactions = budgetStateCalculator.filterPeriodTransactions(
                transactions = transactions,
                settings = s,
                currentPeriodId = currentPeriodId,
                currentPeriodStartedAtMillis = currentPeriodStartedAtMillis,
            )
            budgetStateCalculator.calculateBudgetState(s, periodTransactions, today, paidOccurrences)
        }

        // Plan 016: link ad-hoc spend to recurring templates (amount+date).
        val recurringLinker = RecurringLinker(RecurringExpenseCalculator())
        val links = recurringLinker.link(
            adHoc = displayTx,
            templates = displayTx,
            today = today,
        )
        val linkedTemplateIds = links.values.toSet()
        val adHocById = displayTx.associateBy { it.id }

        fun withPaidCycles(items: List<UpcomingRecurrentItem>) = items.map { item ->
            val paid = recurringLinker.paidCyclesThisYear(item.transaction, links, adHocById, today)
            if (paid > 0) item.copy(paidCyclesThisYear = paid) else item
        }

        val (upcomingInPeriod, futureOutOfPeriod) = buildUpcomingRecurrentItems(
            transactions = displayTx,
            budgetStartDate = startDate,
            budgetEndDate = endDate,
            today = today,
            paidOccurrences = paidOccurrences,
            linkedTemplateIds = linkedTemplateIds,
            occurrenceCharges = displayTx.filter { it.sourceTransactionId != null },
        ).let { (inPeriod, outOfPeriod) -> withPaidCycles(inPeriod) to withPaidCycles(outOfPeriod) }

        val categoryNames = categories.associate { it.id to it.name }
        val groupedCurrent: Map<LocalDate?, List<Transaction>>
        val groupedPast: Map<LocalDate?, List<Transaction>>
        val matchCount: Int
        if (inputs.filter.isActive) {
            val filteredDisplay = filterTransactions(displayTx, inputs.filter, categoryNames)
            val (fCurrent, fPast) = splitPeriodTransactions(
                transactions = filteredDisplay,
                budgetStartDate = startDate,
                budgetEndDate = endDate,
                currentPeriodStartedAtMillis = currentPeriodStartedAtMillis,
                currentPeriodId = currentPeriodId,
                previousPeriodId = previousPeriodId,
            )
            groupedCurrent = buildGroupedCurrentTransactions(
                currentPeriodTransactions = fCurrent,
                displayTransactions = filteredDisplay,
                budgetStartDate = startDate,
                budgetEndDate = endDate,
                today = today,
                paidOccurrences = paidOccurrences,
            )
            groupedPast = if (userSettings?.showPastTransactions == false) {
                emptyMap()
            } else {
                groupTransactionsByDate(fPast)
            }
            // Count of regular (non-virtual) matches across both scopes.
            matchCount = fCurrent.size + fPast.size
        } else {
            groupedCurrent = buildGroupedCurrentTransactions(
                currentPeriodTransactions = currentPeriodTx,
                displayTransactions = displayTx,
                budgetStartDate = startDate,
                budgetEndDate = endDate,
                today = today,
                paidOccurrences = paidOccurrences,
            )
            groupedPast = if (userSettings?.showPastTransactions == false) {
                emptyMap()
            } else {
                groupTransactionsByDate(pastPeriodTx)
            }
            matchCount = displayTx.size
        }

        val creditOwed = transactions.filter { it.isCredit && !it.isDeleted && !it.isCreditPaid }
            .sumOf { it.amount }
        val remainingBudget = budgetState?.remainingToday ?: BigDecimal.ZERO
        val debtAdjustedBalance = remainingBudget.subtract(creditOwed)

        // Auto-expand first date group on initial load
        val autoExpanded = if (inputs.expandedDates.isEmpty()) {
            groupedCurrent.keys.filterNotNull().sortedDescending().take(1).toSet()
        } else {
            inputs.expandedDates
        }

        return HistoryUiState(
            budgetSettings = budgetSettings,
            budgetState = budgetState,
            currentPeriodId = currentPeriodId,
            currentPeriodStartedAtMillis = currentPeriodStartedAtMillis,
            isCreditQuickToggleEnabled = userSettings?.isCreditQuickToggleEnabled ?: false,
            showPastTransactionsSetting = userSettings?.showPastTransactions ?: true,
            tags = categories.map { it.name },
            transactions = transactions,
            editingTransaction = inputs.editingTransaction,
            pendingRemovedTransactions = inputs.pendingRemovedTransactions,
            recurrentToDelete = inputs.recurrentToDelete,
            recurrentToEdit = inputs.recurrentToEdit,
            showDeleteRecurrentDialog = inputs.showDeleteRecurrentDialog,
            expandedTransactionId = inputs.expandedTransactionId,
            expandedDates = autoExpanded,
            showPastPeriod = inputs.showPastPeriod,
            showOutOfPeriodSubscriptions = inputs.showOutOfPeriodSubscriptions,
            showUpcomingRecurrentInPeriod = inputs.showUpcomingRecurrentInPeriod,
            lockSwipeable = inputs.lockSwipeable,
            recurrentPaymentsViewMode = userSettings?.recurrentPaymentsViewMode
                ?: RecurrentPaymentsViewMode.VERTICAL_LIST,
            filter = inputs.filter,
            isFilterActive = inputs.filter.isActive,
            matchCount = matchCount,
            displayTransactions = displayTx,
            groupedCurrentTransactions = groupedCurrent,
            groupedPastTransactions = groupedPast,
            upcomingRecurrentInPeriod = upcomingInPeriod,
            futureRecurrentOutOfPeriod = futureOutOfPeriod,
            creditOwed = creditOwed,
            debtAdjustedBalance = debtAdjustedBalance,
            pendingRefunds = inputs.pendingRefunds,
        )
    }

    private data class UIInputs(
        val expandedDates: Set<LocalDate>,
        val editingTransaction: Transaction?,
        val recurrentToDelete: Transaction?,
        val recurrentToEdit: Transaction?,
        val showDeleteRecurrentDialog: Boolean,
        val showPastPeriod: Boolean,
        val showOutOfPeriodSubscriptions: Boolean,
        val showUpcomingRecurrentInPeriod: Boolean,
        val lockSwipeable: Boolean,
        val expandedTransactionId: Long?,
        val pendingRemovedTransactions: Map<Long, Transaction>,
        val filter: HistoryFilterState = HistoryFilterState(),
        val showSmsReviewDialog: Boolean = false,
        val smsReviewCandidates: List<Transaction> = emptyList(),
        val pendingRefunds: List<Transaction> = emptyList(),
    )

    companion object {
        private const val EXIT_ANIMATION_DURATION_MS = 600L
    }

    override fun onCleared() {
        super.onCleared()
        autoDismissJob?.cancel()
    }
}
