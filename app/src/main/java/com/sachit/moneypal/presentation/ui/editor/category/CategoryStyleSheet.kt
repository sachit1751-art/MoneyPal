package com.sachit.moneypal.presentation.ui.editor.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sachit.moneypal.R
import com.sachit.moneypal.domain.model.Category

/** Emoji avatars offered for category personalization. */
internal val CATEGORY_EMOJI_CHOICES = listOf(
    "🍔", "🛒", "🚗", "🏠", "✈️", "🎬", "💡", "🏥",
    "🎓", "🐶", "🎁", "☕", "📱", "👕", "💪", "💰",
)

/** Color swatches offered for category personalization (ARGB hex). */
internal val CATEGORY_COLOR_CHOICES = listOf(
    "FFE91E63", "FF9C27B0", "FF673AB7", "FF3F51B5",
    "FF2196F3", "FF00BCD4", "FF009688", "FF4CAF50",
    "FF8BC34A", "FFFFC107", "FFFF9800", "FFFF5722",
    "FF795548", "FF607D8B", "FFF44336", "FF9E9E9E",
)

/**
 * Bottom sheet for personalizing a category with an emoji avatar and a
 * color chip. Both selections are optional; "Clear" removes styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryStyleSheet(
    category: Category,
    onDismiss: () -> Unit,
    onApply: (emoji: String?, colorArgb: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedEmoji by remember { mutableStateOf(category.emoji) }
    var selectedColor by remember { mutableStateOf(category.colorArgb) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.category_style_pick_emoji),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(CATEGORY_EMOJI_CHOICES) { emoji ->
                    val isSelected = emoji == selectedEmoji
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable {
                                selectedEmoji = if (isSelected) null else emoji
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.category_style_pick_color),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                CATEGORY_COLOR_CHOICES.take(8).forEach { argb ->
                    ColorSwatch(
                        color = parseArgbSafe(argb),
                        isSelected = argb == selectedColor,
                        onClick = { selectedColor = if (argb == selectedColor) null else argb },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                CATEGORY_COLOR_CHOICES.drop(8).forEach { argb ->
                    ColorSwatch(
                        color = parseArgbSafe(argb),
                        isSelected = argb == selectedColor,
                        onClick = { selectedColor = if (argb == selectedColor) null else argb },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = {
                        selectedEmoji = null
                        selectedColor = null
                        onApply(null, null)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.category_style_clear))
                }
                TextButton(
                    onClick = { onApply(selectedEmoji, selectedColor) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.category_style_apply))
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/** Parses an RRGGBB or AARRGGBB hex string; falls back to gray on bad input. */
internal fun parseArgbSafe(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(if (hex.length == 6) "#FF$hex" else "#$hex"))
}.getOrDefault(Color.Gray)

/** Surface avatar showing a category's emoji (or first letter) in its color. */
@Composable
fun CategoryAvatar(
    category: Category?,
    fallbackName: String,
    modifier: Modifier = Modifier,
) {
    val bgColor = category?.colorArgb?.let { parseArgbSafe(it) }
        ?: MaterialTheme.colorScheme.surfaceVariant
    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
            Text(
                text = category?.emoji ?: fallbackName.trim().take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
