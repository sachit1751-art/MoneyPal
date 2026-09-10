package com.sachit.moneypal.presentation.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sachit.moneypal.R

/**
 * Search field + quick filter chips for the History screen (plan 008).
 * Stateless: every interaction is forwarded as an intent via [onProcessIntent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySearchBar(
    filter: HistoryFilterState,
    isFilterActive: Boolean,
    matchCount: Int,
    tags: List<String>,
    onProcessIntent: (HistoryUiIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        var showAmountSheet by remember { mutableStateOf(false) }

        OutlinedTextField(
            value = filter.query,
            onValueChange = { query ->
                onProcessIntent(HistoryFilterIntent.SetSearchQuery(query))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (isFilterActive) {
                    IconButton(
                        onClick = { onProcessIntent(HistoryFilterIntent.ClearFilters) },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.history_filter_clear),
                        )
                    }
                }
                IconButton(
                    onClick = { showAmountSheet = true },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.history_filter_more),
                    )
                }
            },
            placeholder = {
                Text(text = stringResource(R.string.history_search_hint))
            },
            shape = MaterialTheme.shapes.extraLarge,
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tags.take(TAG_CHIP_LIMIT)) { tag ->
                FilterChip(
                    selected = filter.categoryName == tag,
                    onClick = { onProcessIntent(HistoryFilterIntent.ToggleCategoryName(tag)) },
                    label = { Text(text = tag) },
                )
            }
            item {
                FilterChip(
                    selected = filter.recurrentOnly,
                    onClick = {
                        onProcessIntent(
                            HistoryFilterIntent.ToggleRecurrentOnly(!filter.recurrentOnly)
                        )
                    },
                    label = { Text(text = stringResource(R.string.history_filter_recurrent)) },
                )
            }
            item {
                FilterChip(
                    selected = filter.creditOnly,
                    onClick = {
                        onProcessIntent(HistoryFilterIntent.ToggleCreditOnly(!filter.creditOnly))
                    },
                    label = { Text(text = stringResource(R.string.history_filter_credit)) },
                )
            }
        }

        if (isFilterActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.history_filter_results, matchCount),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showAmountSheet) {
            HistoryAmountFilterSheet(
                currentFilter = filter,
                onProcessIntent = onProcessIntent,
                onDismiss = { showAmountSheet = false },
            )
        }
    }
}

/** Max category chips shown before the list scrolls. */
private const val TAG_CHIP_LIMIT = 8
