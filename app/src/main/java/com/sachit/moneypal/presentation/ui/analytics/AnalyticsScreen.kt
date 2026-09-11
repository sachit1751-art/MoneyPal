package com.sachit.moneypal.presentation.ui.analytics

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AnalyticsScreen(
    activityResultRegistryOwner: ActivityResultRegistryOwner?,
    onNavigateToMainWithWallet: () -> Unit,
    onNavigateToMain: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler {
        viewModel.onClose()
    }

    LaunchedEffect(Unit) {
        viewModel.shareReportText.collect { text ->
            if (text != null) {
                viewModel.consumeShareReport()
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(
                    Intent.createChooser(sendIntent, null)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AnalyticsUiEffect.NavigateToMainWithWallet -> {
                    viewModel.consumeEffect()
                    onNavigateToMainWithWallet()
                }
                is AnalyticsUiEffect.NavigateToMain -> {
                    viewModel.consumeEffect()
                    onNavigateToMain()
                }
                null -> { /* no-op */ }
            }
        }
    }

    Analytics(
        state = uiState.displayState,
        archivedBudgets = uiState.archivedBudgets,
        categories = uiState.categories,
        actions = AnalyticsActions(
            onCreateNewPeriod = {
                viewModel.onCreateNewPeriod()
            },
            onClose = {
                viewModel.onClose()
            },
            onMarkCreditPaid = {
                viewModel.onMarkCreditPaid()
            },
            onPayTransactionClick = { txId ->
                viewModel.onPayTransactionClick(txId)
            },
            onCutoffDayChanged = { day ->
                viewModel.onCutoffDayChanged(day)
            },
            onHistoricalPeriodSelected = { periodId ->
                viewModel.onPeriodSelected(periodId)
            },
            onTutorialCompleted = { hasSpends ->
                viewModel.onTutorialCompleted(hasSpends)
            },
            onGranularityChanged = { granularity ->
                viewModel.onGranularityChanged(granularity)
            },
            onShareReport = {
                viewModel.onShareReport(com.sachit.moneypal.domain.report.ReportScope.MONTHLY)
            },
        activityResultRegistryOwner = activityResultRegistryOwner,
    )
}
