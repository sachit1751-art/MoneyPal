package com.sachit.moneypal.presentation.ui.settings.datahealth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.SdCardAlert
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sachit.moneypal.R
import com.sachit.moneypal.domain.datahealth.DataHealthIssue
import com.sachit.moneypal.domain.datahealth.DataHealthReport
import com.sachit.moneypal.presentation.ui.theme.MinusTheme
import java.time.LocalDate

/**
 * Read-only data health dashboard (plan 049): stat rows for the whole
 * ledger, with amber rows for issues (missing receipts, duplicate ids).
 * No fix-all buttons — review happens in History, per plan scope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataHealthScreen(
    onBack: () -> Unit,
    onReviewIssue: (DataHealthIssue) -> Unit = {},
    viewModel: DataHealthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.data_health_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.data_health_back_cd),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.loadFailed -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.data_health_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {                  val report = uiState.report ?: return@Scaffold
                      DataHealthContent(
                          report = report,
                          onReviewIssue = onReviewIssue,
                          modifier = Modifier
                              .fillMaxSize()
                              .padding(paddingValues),
                      )
            }
        }
    }
}

@Composable
private fun DataHealthContent(
    report: DataHealthReport,
    onReviewIssue: (DataHealthIssue) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 8.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (report.hasIssues) {
            item(key = "issues_header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.data_health_issues_found),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (report.missingAttachmentCount > 0) {
                      item(key = "issue_missing_attachments") {
                          IssueRow(
                              icon = Icons.Filled.LinkOff,
                              text = stringResource(
                                  R.string.data_health_issue_missing_attachments,
                                  report.missingAttachmentCount,
                              ),
                              onReview = { onReviewIssue(DataHealthIssue.MISSING_RECEIPTS) },
                          )
                      }
                  }
                  if (report.duplicateClientGeneratedIdCount > 0) {
                      item(key = "issue_duplicates") {
                          IssueRow(
                              icon = Icons.Filled.SdCardAlert,
                              text = stringResource(
                                  R.string.data_health_issue_duplicate_ids,
                                  report.duplicateClientGeneratedIdCount,
                              ),
                              onReview = { onReviewIssue(DataHealthIssue.DUPLICATE_IDS) },
                          )
                      }
                  }
        }

        item(key = "stats_header") {
            Text(
                text = stringResource(R.string.data_health_overview_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item(key = "stat_total") {
            StatRow(
                icon = Icons.Filled.Receipt,
                label = stringResource(R.string.data_health_total_transactions),
                value = report.totalTransactions.toString(),
            )
        }
        item(key = "stat_income") {
            StatRow(
                icon = Icons.Filled.CallReceived,
                label = stringResource(R.string.data_health_income_count),
                value = report.incomeCount.toString(),
            )
        }
        item(key = "stat_expense") {
            StatRow(
                icon = Icons.Filled.Assignment,
                label = stringResource(R.string.data_health_expense_count),
                value = report.expenseCount.toString(),
            )
        }
        item(key = "stat_sms") {
            StatRow(
                icon = Icons.Filled.Sms,
                label = stringResource(R.string.data_health_sms_captured),
                value = report.smsCapturedCount.toString(),
            )
        }
        item(key = "stat_confidence") {
            StatRow(
                icon = Icons.Filled.Preview,
                label = stringResource(R.string.data_health_median_confidence),
                value = report.medianSmsConfidence?.toPlainString()
                    ?: stringResource(R.string.data_health_no_sms_value),
            )
        }
        item(key = "stat_attachments") {
            StatRow(
                icon = Icons.Filled.Attachment,
                label = stringResource(R.string.data_health_attachments),
                value = report.attachmentCount.toString(),
            )
        }
        item(key = "stat_oldest") {
            StatRow(
                icon = Icons.Filled.Event,
                label = stringResource(R.string.data_health_oldest_entry),
                value = report.oldestTransactionDate?.toString()
                    ?: stringResource(R.string.data_health_no_entries),
            )
        }
    }
}

@Composable
private fun StatRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun IssueRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onReview: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReview) {
                Text(stringResource(R.string.data_health_review_action))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DataHealthScreenPreview() {
    MinusTheme {
        DataHealthContent(
            onReviewIssue = {},
            report = DataHealthReport(
                totalTransactions = 128,
                incomeCount = 12,
                expenseCount = 116,
                smsCapturedCount = 40,
                medianSmsConfidence = java.math.BigDecimal("88.0"),
                attachmentCount = 6,
                missingAttachmentCount = 2,
                duplicateClientGeneratedIdCount = 1,
                oldestTransactionDate = LocalDate.of(2026, 3, 15),
            ),
        )
    }
}
