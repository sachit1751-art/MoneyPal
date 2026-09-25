package com.sachit.moneypal.presentation.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.lock.AutoLockTimeout
import com.sachit.moneypal.domain.model.AppColorScheme
import com.sachit.moneypal.domain.model.ContrastMode
import com.sachit.moneypal.domain.model.PeriodMappingMode
import com.sachit.moneypal.domain.model.SavingsPreferences
import com.sachit.moneypal.domain.model.ThemeMode
import com.sachit.moneypal.domain.model.TypographyMode
import com.sachit.moneypal.domain.usecase.UpdatePeriodEndNotificationTimeUseCase
import com.sachit.moneypal.presentation.appColorScheme
import com.sachit.moneypal.presentation.appContrast
import com.sachit.moneypal.presentation.appTheme
import com.sachit.moneypal.presentation.appTypography
import com.sachit.moneypal.presentation.dynamicColorEnabled
import com.sachit.moneypal.presentation.isAmoledEnabled
import com.sachit.moneypal.presentation.lock.AppLockController
import com.sachit.moneypal.presentation.ui.history.RecurrentPaymentsViewMode
import com.sachit.moneypal.domain.report.MonthlyReportBuilder
import com.sachit.moneypal.presentation.report.MonthlyReportPdfWriter
import com.sachit.moneypal.presentation.report.MonthlyReportShareManager
import com.sachit.moneypal.presentation.ui.settings.csv.CsvTransferEntryPoint
import com.sachit.moneypal.presentation.ui.settings.csv.CsvTransferManager
import com.sachit.moneypal.presentation.util.CensorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject
import android.provider.Settings as AndroidSettings

data class SettingsUiState(
    val currentTheme: String = "System",
    val currentTypography: String = "Expressive",
    val currentContrast: String = "Normal",
    val currentColorScheme: AppColorScheme = AppColorScheme.BRAND,
    val currentLanguage: String = "English",
    val isMaterialYouEnabled: Boolean = false,
    val isRoundedFontEnabled: Boolean = true,
    val isAmoledEnabled: Boolean = false,
    val isCreditQuickToggleEnabled: Boolean = false,
    val showPastTransactions: Boolean = true,
    val isCategoryPickerDirectPopupEnabled: Boolean = false,
    val isCategoryGridModeEnabled: Boolean = false,
    val recurrentPaymentsViewMode: RecurrentPaymentsViewMode = RecurrentPaymentsViewMode.VERTICAL_LIST,
    val notificationHour: Int = 9,
    val notificationMinute: Int = 0,
    val recurrentNotificationHour: Int = 8,
    val recurrentNotificationMinute: Int = 0,
    val exactAlarmEnabled: Boolean = true,
    val notificationPermissionGranted: Boolean = false,
    val isCensored: Boolean = false,
    val periodMappingMode: PeriodMappingMode = PeriodMappingMode.ACTIVE_BUDGET,
    val savingsPreferences: SavingsPreferences = SavingsPreferences.DEFAULT,
    val creditCardCutoffDay: Int? = null,
    val smsCaptureEnabled: Boolean = false,
    val smsPermissionGranted: Boolean = false,
    val appLockEnabled: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATELY,
    val widgetsHideAmounts: Boolean = false,
    val thresholdAlertsEnabled: Boolean = false,
    val envelopeAlertsEnabled: Boolean = false,
    val notificationQuickActions: Boolean = true,
    val weeklyDigestEnabled: Boolean = false,
    val refundNudgeEnabled: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val autoBackupTreeUri: String = "",
    val autoBackupLastRunAt: Long = 0L,
)

sealed interface SettingsUiEffect {
    data object NavigateToBugReport : SettingsUiEffect
    data object NavigateBack : SettingsUiEffect
    data object AppLockUnavailable : SettingsUiEffect
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val budgetRepository: BudgetRepository,
    private val updateNotificationTimeUseCase: UpdatePeriodEndNotificationTimeUseCase,
    private val censorManager: CensorManager,
    private val appLockController: AppLockController,
) : ViewModel() {

    private val _notificationPermissionGranted = MutableStateFlow(false)
    private val _smsPermissionGranted = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        budgetRepository.getBudgetSettings(),
        censorManager.isCensored,
        _notificationPermissionGranted,
        _smsPermissionGranted,
    ) { settings, budgetSettings, isCensored, permissionGranted, smsGranted ->
        SettingsUiState(
            currentTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> "Light"
                ThemeMode.NIGHT -> "Dark"
                else -> "System"
            },
            currentTypography = when (settings.typographyMode) {
                TypographyMode.CONDENSED -> "Condensed"
                TypographyMode.SYSTEM -> "System"
                else -> "Expressive"
            },
            currentContrast = when (settings.contrastMode) {
                ContrastMode.MEDIUM -> "Medium"
                ContrastMode.HIGH -> "High"
                else -> "Normal"
            },
            currentColorScheme = settings.colorScheme,
            isMaterialYouEnabled = settings.dynamicColorEnabled,
            isRoundedFontEnabled = settings.isRoundedFontEnabled,
            isAmoledEnabled = settings.isAmoledEnabled,
            isCreditQuickToggleEnabled = settings.isCreditQuickToggleEnabled,
            showPastTransactions = settings.showPastTransactions,
            isCategoryPickerDirectPopupEnabled = settings.categoryPickerDirectPopupEnabled,
            isCategoryGridModeEnabled = settings.categoryGridModeEnabled,
            currentLanguage = settings.language,
            recurrentPaymentsViewMode = settings.recurrentPaymentsViewMode,
            notificationHour = settings.notificationHour,
            notificationMinute = settings.notificationMinute,
            recurrentNotificationHour = settings.recurrentNotificationHour,
            recurrentNotificationMinute = settings.recurrentNotificationMinute,
            exactAlarmEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager =
                    context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else true,
            notificationPermissionGranted = permissionGranted,
            isCensored = isCensored,
            periodMappingMode = settings.periodMappingMode,
            savingsPreferences = settings.savingsPreferences,
            creditCardCutoffDay = budgetSettings?.creditCardCutoffDay,
            smsCaptureEnabled = settings.smsCaptureEnabled,
            smsPermissionGranted = smsGranted,
            appLockEnabled = settings.appLockEnabled,
            autoLockTimeout = settings.autoLockTimeout,
            widgetsHideAmounts = settings.widgetsHideAmounts,
            thresholdAlertsEnabled = settings.thresholdAlertsEnabled,
            notificationQuickActions = settings.notificationQuickActions,
            weeklyDigestEnabled = settings.weeklyDigestEnabled,
            refundNudgeEnabled = settings.refundNudgeEnabled,
            autoBackupEnabled = settings.autoBackupEnabled,
            autoBackupTreeUri = settings.autoBackupTreeUri,
            autoBackupLastRunAt = settings.autoBackupLastRunAt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    private val _effects = MutableStateFlow<SettingsUiEffect?>(null)
    val effects: StateFlow<SettingsUiEffect?> = _effects.asStateFlow()

    /** True while the restore-password dialog should be visible (plan 011). */
    private val _restoreNeedsPassword = MutableStateFlow(false)
    val restoreNeedsPassword: StateFlow<Boolean> = _restoreNeedsPassword.asStateFlow()
    private var pendingRestoreUri: Uri? = null

    private var csvTransferManager: CsvTransferManager? = null
    private var importLauncher: ActivityResultLauncher<Array<String>>? = null

    /** Plan 044: monthly report pipeline (pure builder + PDF writer + share). */
    private val monthlyReportBuilder = MonthlyReportBuilder()
    private var monthlyReportPdfWriter: MonthlyReportPdfWriter? = null
    private var monthlyReportShareManager: MonthlyReportShareManager? = null

    init {
        refreshNotificationPermission()
        refreshSmsPermission()
    }

    fun refreshSmsPermission() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        _smsPermissionGranted.value = granted
    }

    fun onSmsCaptureToggle() {
        val newValue = !uiState.value.smsCaptureEnabled
        viewModelScope.launch {
            settingsRepository.setSmsCaptureEnabled(newValue)
        }
    }

    fun onThresholdAlertsToggle() {
        val newValue = !uiState.value.thresholdAlertsEnabled
        viewModelScope.launch {
            settingsRepository.setThresholdAlertsEnabled(newValue)
        }
    }

    /** Category-envelope alerts toggle (plan 007). */
    fun onEnvelopeAlertsToggle() {
        val newValue = !uiState.value.envelopeAlertsEnabled
        viewModelScope.launch {
            settingsRepository.setEnvelopeAlertsEnabled(newValue)
        }
    }

    /** Mark-paid/snooze buttons on recurring reminders (plan 045). */
    fun onNotificationQuickActionsToggle() {
        val newValue = !uiState.value.notificationQuickActions
        viewModelScope.launch {
            settingsRepository.setNotificationQuickActions(newValue)
        }
    }

    /** Weekly digest toggle (plan 003): also enqueues/cancels the periodic work. */
    fun onWeeklyDigestToggle() {
        val newValue = !uiState.value.weeklyDigestEnabled
        viewModelScope.launch {
            settingsRepository.setWeeklyDigestEnabled(newValue)
            weeklyDigestScheduler.reschedule(newValue)
        }
    }

    /** Refund nudge toggle (plan 017): also enqueues/cancels the periodic work. */
    fun onRefundNudgeToggle() {
        val newValue = !uiState.value.refundNudgeEnabled
        viewModelScope.launch {
            settingsRepository.setRefundNudgeEnabled(newValue)
            refundNudgeScheduler.reschedule(newValue)
        }
    }

    /** Auto-backup toggle (plan 004): also enqueues/cancels the periodic work. */
    fun onAutoBackupToggle() {
        val newValue = !uiState.value.autoBackupEnabled
        viewModelScope.launch {
            settingsRepository.setAutoBackupEnabled(newValue)
            autoBackupScheduler.reschedule(newValue)
        }
    }

    fun onAutoBackupFolderChosen(treeUriString: String) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupTreeUri(treeUriString)
        }
    }

    /** Manual "Back up now" via the same write path as the scheduled backup. */
    fun onAutoBackupNow() {
        viewModelScope.launch {
            // forceRun: a manual trigger must not be gated by the 15-day
            // cadence (runNow is the scheduler-gated entry, plan 029).
            val ok = autoBackupScheduler.forceRun()
            _effects.value = if (ok) {
                SettingsUiEffect.NavigateBack
            } else {
                null
            }
        }
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface SchedulerEntryPoint {
        fun weeklyDigestScheduler(): com.sachit.moneypal.presentation.notification.WeeklyDigestScheduler

        fun autoBackupScheduler(): com.sachit.moneypal.presentation.notification.AutoBackupScheduler

        fun refundNudgeScheduler(): com.sachit.moneypal.presentation.notification.RefundNudgeScheduler
    }

    private val weeklyDigestScheduler: com.sachit.moneypal.presentation.notification.WeeklyDigestScheduler
        get() = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            SchedulerEntryPoint::class.java,
        ).weeklyDigestScheduler()

    private val refundNudgeScheduler: com.sachit.moneypal.presentation.notification.RefundNudgeScheduler
        get() = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            SchedulerEntryPoint::class.java,
        ).refundNudgeScheduler()

    private val autoBackupScheduler: com.sachit.moneypal.presentation.notification.AutoBackupScheduler
        get() = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            SchedulerEntryPoint::class.java,
        ).autoBackupScheduler()

    /**
     * App lock toggle (plan 009). Refuses to enable when the device has no
     * biometric/credential authenticator enrolled — the toggle must never be
     * left ON in a state that would permanently lock the user out.
     */
    fun onAppLockToggle() {
        val newValue = !uiState.value.appLockEnabled
        if (newValue && !appLockController.canAuthenticate()) {
            _effects.value = SettingsUiEffect.AppLockUnavailable
            return
        }
        viewModelScope.launch {
            settingsRepository.setAppLockEnabled(newValue)
        }
    }

    /** Re-lock delay after leaving the app (plan 041). */
    fun onAutoLockTimeoutSelected(timeout: AutoLockTimeout) {
        viewModelScope.launch {
            settingsRepository.setAutoLockTimeout(timeout)
        }
    }

    /**
     * Widget privacy redaction (plan 042): persists the flag and re-renders
     * every money widget so the change is visible immediately.
     */
    fun onWidgetsHideAmountsToggle() {
        val newValue = !uiState.value.widgetsHideAmounts
        viewModelScope.launch {
            settingsRepository.setWidgetsHideAmounts(newValue)
            widgetPrivacyRefresher.refreshAll(context)
        }
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface WidgetPrivacyEntryPoint {
        fun widgetPrivacyRefresher(): com.sachit.moneypal.presentation.widget.WidgetPrivacyRefresher
    }

    private val widgetPrivacyRefresher: com.sachit.moneypal.presentation.widget.WidgetPrivacyRefresher
        get() = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetPrivacyEntryPoint::class.java,
        ).widgetPrivacyRefresher()

    fun refreshNotificationPermission() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        logcat("SACHIT:Settings") { "refreshNotificationPermission -> granted=$granted" }
        _notificationPermissionGranted.value = granted
    }

    fun onOpenAppSettings() {
        logcat("SACHIT:Settings") { "onOpenAppSettings" }
        val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun setCsvTransferManager(manager: CsvTransferManager) {
        csvTransferManager = manager
    }

    fun setImportLauncher(launcher: ActivityResultLauncher<Array<String>>) {
        importLauncher = launcher
    }

    fun onThemeChange(themeMode: String) {
        val newMode = when (themeMode) {
            "Light" -> ThemeMode.LIGHT
            "Dark" -> ThemeMode.NIGHT
            else -> ThemeMode.SYSTEM
        }
        context.appTheme = newMode
        viewModelScope.launch {
            settingsRepository.setThemeMode(newMode)
        }
    }

    fun onTypographyChange(typographyMode: String) {
        val newMode = when (typographyMode) {
            "System" -> TypographyMode.SYSTEM
            "Default" -> TypographyMode.DEFAULT
            "Condensed" -> TypographyMode.CONDENSED
            else -> TypographyMode.EXPRESSIVE
        }
        context.appTypography = newMode
        viewModelScope.launch {
            settingsRepository.setTypographyMode(newMode)
        }
    }

    fun onContrastChange(contrastMode: String) {
        val newMode = when (contrastMode) {
            "Medium" -> ContrastMode.MEDIUM
            "High" -> ContrastMode.HIGH
            else -> ContrastMode.NORMAL
        }
        context.appContrast = newMode
        viewModelScope.launch {
            settingsRepository.setContrastMode(newMode)
        }
    }

    fun onColorSchemeChange(colorScheme: AppColorScheme) {
        context.appColorScheme = colorScheme
        viewModelScope.launch {
            settingsRepository.setAppColorScheme(colorScheme)
        }
    }

    fun onLanguageChange(language: String) {
        viewModelScope.launch {
            settingsRepository.setLanguage(language)
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(language)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    fun onMaterialYouToggle() {
        val newValue = !uiState.value.isMaterialYouEnabled
        context.dynamicColorEnabled = newValue
        viewModelScope.launch {
            settingsRepository.setDynamicColorEnabled(newValue)
        }
    }

    fun onRoundedFontToggle() {
        val newValue = !uiState.value.isRoundedFontEnabled
        viewModelScope.launch {
            settingsRepository.setRoundedFontEnabled(newValue)
        }
    }

    fun onAmoledToggle() {
        val newValue = !uiState.value.isAmoledEnabled
        context.isAmoledEnabled = newValue
        viewModelScope.launch {
            settingsRepository.setAmoledEnabled(newValue)
        }
    }

    fun onCensorModeToggle() {
        censorManager.setCensored(!uiState.value.isCensored)
    }

    fun onCreditQuickToggleFeatureToggle() {
        val newValue = !uiState.value.isCreditQuickToggleEnabled
        viewModelScope.launch {
            settingsRepository.setCreditQuickToggleEnabled(newValue)
        }
    }

    fun onCutoffDayChange(day: Int) {
        viewModelScope.launch {
            val currentSettings = budgetRepository.getBudgetSettingsSync()
            if (currentSettings != null) {
                budgetRepository.saveBudgetSettings(currentSettings.copy(creditCardCutoffDay = day))
            }
        }
    }

    fun onShowPastTransactionsToggle() {
        val newValue = !uiState.value.showPastTransactions
        viewModelScope.launch {
            settingsRepository.setShowPastTransactions(newValue)
        }
    }

    fun onCategoryPickerDirectPopupFeatureToggle() {
        val newValue = !uiState.value.isCategoryPickerDirectPopupEnabled
        viewModelScope.launch {
            settingsRepository.setCategoryPickerDirectPopupEnabled(newValue)
        }
    }

    fun onCategoryGridModeToggle() {
        val newValue = !uiState.value.isCategoryGridModeEnabled
        viewModelScope.launch {
            settingsRepository.setCategoryGridModeEnabled(newValue)
        }
    }

    fun onRecurrentPaymentsViewModeChange(mode: RecurrentPaymentsViewMode) {
        viewModelScope.launch {
            settingsRepository.setRecurrentPaymentsViewMode(mode)
        }
    }

    fun onNotificationTimeChange(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setNotificationTime(hour, minute)
            updateNotificationTimeUseCase(hour, minute)
        }
    }

    fun onRecurrentNotificationTimeChange(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setRecurrentNotificationTime(hour, minute)
            updateNotificationTimeUseCase.updateRecurrentNotificationTime(hour, minute)
        }
    }

    fun onPeriodMappingModeChange(mode: PeriodMappingMode) {
        viewModelScope.launch {
            settingsRepository.setPeriodMappingMode(mode)
        }
    }

    fun onSavingsPreferencesChange(prefs: SavingsPreferences) {
        viewModelScope.launch {
            settingsRepository.setSavingsPreferences(prefs)
        }
    }

    fun onOpenExactAlarmSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${context.packageName}".toUri()
            }
        } else {
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            }
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun onExportCsv() {
        viewModelScope.launch {
            csvTransferManager?.exportAndShareCsv()
        }
    }

    /** Called by the screen to inject the Hilt entry-point managers (plan 044). */
    fun setMonthlyReportManagers(writer: MonthlyReportPdfWriter, shareManager: MonthlyReportShareManager) {
        monthlyReportPdfWriter = writer
        monthlyReportShareManager = shareManager
    }

    /**
     * Plan 044: builds the current-period report from live state, renders the
     * PDF off the main thread, and fires the share sheet. Failures surface as
     * a toast via the share manager; nothing is written when state is absent.
     */
    fun onExportReportPdf() {
        viewModelScope.launch {
            val writer = monthlyReportPdfWriter
            val shareManager = monthlyReportShareManager
            if (writer == null || shareManager == null) {
                _reportPdfReady.value = false
                return@launch
            }
            runCatching {
                val settings = budgetRepository.getBudgetSettings().first()
                if (settings == null) {
                    _reportPdfReady.value = false
                    return@launch
                }
                val data = monthlyReportBuilder.build(
                    transactions = budgetRepository.getTransactions().first(),
                    periodStart = settings.startDate,
                    periodEnd = settings.getPeriodEndDate(),
                    currencyCode = settings.currencyCode,
                    budget = settings.totalBudget,
                )
                val file = writer.write(data)
                shareManager.share(file)
            }.onFailure { error ->
                logcat { "Monthly report export failed: $error" }
                _reportPdfReady.value = false
            }.onSuccess { shared ->
                _reportPdfReady.value = shared
            }
        }
    }

    private val _reportPdfReady = MutableStateFlow<Boolean?>(null)

    /** One-shot report export outcome: true = shared, false = failed, null = idle. */
    val reportPdfReady: StateFlow<Boolean?> = _reportPdfReady.asStateFlow()

    fun consumeReportPdfReady() {
        _reportPdfReady.value = null
    }

    fun onImportCsv() {
        importLauncher?.launch(arrayOf("text/*", "text/csv", "application/csv"))
    }

    fun onImportResult(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            csvTransferManager?.enqueueImport(uri.toString())
        }
    }

    // ---- Full backup / restore (plan 010 + SAF folder target) ----

    private var backupTransferManager: com.sachit.moneypal.presentation.ui.settings.backup.BackupTransferManager? = null
    private var backupFolderLauncher: androidx.activity.result.ActivityResultLauncher<Uri?>? = null
    private var restoreLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null

    fun setBackupTransferManager(manager: com.sachit.moneypal.presentation.ui.settings.backup.BackupTransferManager) {
        backupTransferManager = manager
    }

    fun setBackupFolderLauncher(launcher: androidx.activity.result.ActivityResultLauncher<Uri?>) {
        backupFolderLauncher = launcher
    }

    fun setRestoreBackupLauncher(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
        restoreLauncher = launcher
    }

    fun onCreateBackup(password: CharArray?) {
        viewModelScope.launch {
            val uri = if (password != null) {
                backupTransferManager?.exportEncryptedBackup(password)
            } else {
                backupTransferManager?.exportBackup()
            }
            if (uri != null) backupTransferManager?.toastSaved()
        }
    }

    fun onExportBackupToFolder() {
        backupFolderLauncher?.launch(null)
    }

    fun onBackupFolderResult(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val saved = backupTransferManager?.exportToFolder(uri) ?: false
            if (saved) backupTransferManager?.toastSaved()
        }
    }

    fun onRestoreBackup() {
        restoreLauncher?.launch(arrayOf("application/json"))
    }

    fun onRestoreBackupResult(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            when (val outcome = backupTransferManager?.restoreFrom(uri)) {
                is com.sachit.moneypal.presentation.ui.settings.backup.BackupTransferManager.RestoreOutcome.Success ->
                    toast(outcome.message)
                is com.sachit.moneypal.presentation.ui.settings.backup.BackupTransferManager.RestoreOutcome.PasswordRequired -> {
                    pendingRestoreUri = uri
                    _restoreNeedsPassword.value = true
                }
                is com.sachit.moneypal.presentation.ui.settings.backup.BackupTransferManager.RestoreOutcome.Failure ->
                    toast(outcome.message)
                null -> Unit
            }
        }
    }

    /** Password submitted from the restore-password dialog (plan 011). */
    fun onRestorePasswordEntered(password: CharArray) {
        val uri = pendingRestoreUri ?: return
        _restoreNeedsPassword.value = false
        viewModelScope.launch {
            when (val outcome = backupTransferManager?.restoreFrom(uri, password)) {
                is com.sachit.moneypal.presentation.ui.settings.backup.BackupTransferManager.RestoreOutcome.Success ->
                    toast(outcome.message)
                is com.sachit.moneypal.presentation.ui.settings.backup.BackupTransferManager.RestoreOutcome.PasswordRequired ->
                    toast(context.getString(com.sachit.moneypal.R.string.backup_password_wrong))
                is com.sachit.moneypal.presentation.ui.settings.backup.BackupTransferManager.RestoreOutcome.Failure ->
                    toast(outcome.message)
                null -> Unit
            }
        }
    }

    fun onRestorePasswordDialogDismissed() {
        _restoreNeedsPassword.value = false
        pendingRestoreUri = null
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }

    fun onResetTutorial() {
        viewModelScope.launch {
            settingsRepository.resetTutorials()
        }
    }

    fun onBugReportClick() {
        _effects.value = SettingsUiEffect.NavigateToBugReport
    }

    fun onBack() {
        _effects.value = SettingsUiEffect.NavigateBack
    }

    fun consumeEffect() {
        _effects.value = null
    }
}
