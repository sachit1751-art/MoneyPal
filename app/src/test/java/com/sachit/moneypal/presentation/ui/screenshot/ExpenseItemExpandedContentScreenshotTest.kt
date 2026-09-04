package com.sachit.moneypal.presentation.ui.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.sachit.moneypal.domain.model.Transaction
import com.sachit.moneypal.presentation.ui.theme.MinusTheme
import com.sachit.moneypal.presentation.ui.theme.component.expense.ExpenseItemExpandedContent
import com.sachit.moneypal.presentation.util.font.format.symbolOnlyCurrencyFormat
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

class ExpenseItemExpandedContentScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        renderingMode = SessionParams.RenderingMode.SHRINK,
        maxPercentDifference = 10.0,
    )

    @Test
    fun expenseDetailReadOnly() {
        Locale.setDefault(Locale.US)
        paparazzi.snapshot {
            MinusTheme {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    ExpenseItemExpandedContent(
                        transaction = Transaction(
                            id = 1L,
                            amount = BigDecimal("42.50"),
                            comment = "Lunch with team",
                            date = LocalDate.of(2026, 1, 15).atTime(12, 30),
                            periodId = 7L,
                        ),
                        currencyFormat = symbolOnlyCurrencyFormat("USD"),
                        onMarkAsPaid = {},
                        onEdit = {},
                        onDelete = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
