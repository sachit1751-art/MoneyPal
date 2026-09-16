package com.sachit.moneypal.presentation.ui.history

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sachit.moneypal.R

/** Entry chip for the low-confidence SMS review inbox (plan 015). */
@Composable
internal fun ReviewSmsChip(
    count: Int,
    onClick: () -> Unit,
) {
    SuggestionChip(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            labelColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        label = { Text(stringResource(R.string.sms_review_chip, count)) },
    )
}
