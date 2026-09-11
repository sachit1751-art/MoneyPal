package com.sachit.moneypal.presentation.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sachit.moneypal.presentation.ui.settings.csv.CsvTransferEntryPoint
import dagger.hilt.android.EntryPointAccessors

@Composable
fun SettingsScreen(
    onNavigateToBugReport: () -> Unit,
    onNavigateToChangelog: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        viewModel.onImportResult(uri)
    }

    val backupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        viewModel.onBackupFolderResult(uri)
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        viewModel.onRestoreBackupResult(uri)
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        viewModel.refreshSmsPermission()
    }

    LaunchedEffect(Unit) {
        val manager = EntryPointAccessors
            .fromApplication(context.applicationContext, CsvTransferEntryPoint::class.java)
            .csvTransferManager()
        viewModel.setCsvTransferManager(manager)
        viewModel.setImportLauncher(importLauncher)

        val backupManager = EntryPointAccessors
            .fromApplication(
                context.applicationContext,
                com.sachit.moneypal.presentation.ui.settings.backup.BackupTransferEntryPoint::class.java,
            )
            .backupTransferManager()
        viewModel.setBackupTransferManager(backupManager)
        viewModel.setBackupFolderLauncher(backupFolderLauncher)
        viewModel.setRestoreBackupLauncher(restoreBackupLauncher)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsUiEffect.NavigateToBugReport -> {
                    viewModel.consumeEffect()
                    onNavigateToBugReport()
                }

                is SettingsUiEffect.NavigateBack -> {
                    viewModel.consumeEffect()
                    onNavigateBack()
                }

                is SettingsUiEffect.AppLockUnavailable -> {
                    viewModel.consumeEffect()
                    android.widget.Toast.makeText(
                        context,
                        com.sachit.moneypal.R.string.app_lock_no_authenticator,
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }

                null -> { /* no-op */
                }
            }
        }
    }

    Settings(
        isCensored = uiState.isCensored,
        isCreditQuickToggleFeatureEnabled = uiState.isCreditQuickToggleEnabled,
        showPastTransactions = uiState.showPastTransactions,
        isCategoryPickerDirectPopupEnabled = uiState.isCategoryPickerDirectPopupEnabled,
        isCategoryGridModeEnabled = uiState.isCategoryGridModeEnabled,
        onCategoryPickerDirectPopupFeatureToggle = viewModel::onCategoryPickerDirectPopupFeatureToggle,
        onCategoryGridModeToggle = viewModel::onCategoryGridModeToggle,
        onShowPastTransactionsToggle = viewModel::onShowPastTransactionsToggle,
        recurrentPaymentsViewMode = uiState.recurrentPaymentsViewMode,
        notificationHour = uiState.notificationHour,
        notificationMinute = uiState.notificationMinute,
        recurrentNotificationHour = uiState.recurrentNotificationHour,
        recurrentNotificationMinute = uiState.recurrentNotificationMinute,
        exactAlarmEnabled = uiState.exactAlarmEnabled,
        notificationPermissionGranted = uiState.notificationPermissionGranted,
        onCensorModeToggle = viewModel::onCensorModeToggle,
        onCreditQuickToggleFeatureToggle = viewModel::onCreditQuickToggleFeatureToggle,
        onRecurrentPaymentsViewModeChange = viewModel::onRecurrentPaymentsViewModeChange,
        onNotificationTimeChange = viewModel::onNotificationTimeChange,
        onRecurrentNotificationTimeChange = viewModel::onRecurrentNotificationTimeChange,
        onOpenExactAlarmSettings = viewModel::onOpenExactAlarmSettings,
        onOpenNotificationSettings = {
            viewModel.onOpenAppSettings()
            viewModel.refreshNotificationPermission()
        },
        periodMappingMode = uiState.periodMappingMode,
        onPeriodMappingModeChange = viewModel::onPeriodMappingModeChange,
        savingsPreferences = uiState.savingsPreferences,
        onSavingsPreferencesChange = viewModel::onSavingsPreferencesChange,
        onExportCsv = viewModel::onExportCsv,
        onImportCsv = viewModel::onImportCsv,
        onCreateBackup = viewModel::onCreateBackup,
        onExportBackupToFolder = viewModel::onExportBackupToFolder,
        onRestoreBackup = viewModel::onRestoreBackup,
        onResetTutorial = viewModel::onResetTutorial,
        smsCaptureEnabled = uiState.smsCaptureEnabled,
        smsPermissionGranted = uiState.smsPermissionGranted,
        onSmsCaptureToggle = viewModel::onSmsCaptureToggle,
        onRequestSmsPermission = {
            smsPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.READ_SMS,
                )
            )
        },
        onOpenSmsAppSettings = {
            viewModel.onOpenAppSettings()
            viewModel.refreshSmsPermission()
        },
        appLockEnabled = uiState.appLockEnabled,
        onAppLockToggle = viewModel::onAppLockToggle,
        thresholdAlertsEnabled = uiState.thresholdAlertsEnabled,
        onThresholdAlertsToggle = viewModel::onThresholdAlertsToggle,
        onBugReportClick = viewModel::onBugReportClick,
        onNavigateToChangelog = onNavigateToChangelog,
        onNavigateToAppearance = onNavigateToAppearance,
        onBack = viewModel::onBack,
    )
}
