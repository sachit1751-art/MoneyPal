package com.sachit.moneypal.presentation.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.sachit.moneypal.domain.report.MonthlyReportData
import com.sachit.moneypal.presentation.util.font.format.formatCurrencySymbolOnly
import dagger.hilt.android.qualifiers.ApplicationContext
import logcat.logcat
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a [MonthlyReportData] to a one-page A4 PDF (plan 044) using only
 * `android.graphics.pdf.PdfDocument` + Canvas text — no WebView, no extra
 * dependency (FOSS-safe). Text is sized in SP via [sp] so system font scale
 * is respected. Written to `cacheDir/reports/` and shared via FileProvider.
 */
@Singleton
class MonthlyReportPdfWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Renders [data] to `cacheDir/reports/` and returns the written file. */
    suspend fun write(data: MonthlyReportData): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, REPORTS_DIR).apply { mkdirs() }
        val safeLabel = data.periodLabel.replace(Regex("[^A-Za-z0-9]"), "_")
        val file = File(dir, "MoneyPal_report_$safeLabel.pdf")

        val document = PdfDocument()
        try {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, PAGE_NUMBER).create()
            )
            drawReport(page.canvas, data)
            document.finishPage(page)
            file.outputStream().use { out -> document.writeTo(out) }
            logcat { "Monthly report PDF written: ${file.absolutePath} bytes=${file.length()}" }
        } finally {
            document.close()
        }
        file
    }

    /** Pure-ish layout pass, split out so page math stays readable. */
    private fun drawReport(canvas: Canvas, data: MonthlyReportData) {
        var y = MARGIN_PT

        val titlePaint = paint(TEXT_TITLE_SP, bold = true)
        canvas.drawText(data.periodLabel, MARGIN_PT, y, titlePaint)
        y += SP * 1.6f

        val subtitlePaint = paint(TEXT_SMALL_SP)
        canvas.drawText(
            "${data.periodStart} – ${data.periodEnd}  ·  ${data.currencyCode}",
            MARGIN_PT,
            y,
            subtitlePaint,
        )
        y += SP * 2f

        // Totals block.
        val money = { value: java.math.BigDecimal ->
            formatCurrencySymbolOnly(value, data.currencyCode)
        }
        val labelPaint = paint(TEXT_BODY_SP, color = Color.DKGRAY)
        val valuePaint = paint(TEXT_BODY_SP, bold = true)
        val row = { label: String, value: String ->
            canvas.drawText(label, MARGIN_PT, y, labelPaint)
            canvas.drawText(value, PAGE_WIDTH_PT - MARGIN_PT, y, valuePaint.apply {
                textAlign = Paint.Align.RIGHT
            })
            y += SP * 1.5f
        }
        row("Total spent", money(data.totalSpent))
        row("Total income", money(data.totalIncome))
        data.budget?.let { budget ->
            row("Budget", money(budget))
            row("Remaining", money(budget.subtract(data.totalSpent)))
        }
        row("Entries", "${data.spendCount} spent · ${data.incomeCount} income")
        row("No-spend days", data.noSpendDays.toString())
        y += SP * 1f

        // Category table.
        if (data.categories.isNotEmpty()) {
            canvas.drawText("Categories", MARGIN_PT, y, paint(TEXT_BODY_SP, bold = true))
            y += SP * 1.5f
            data.categories.forEach { category ->
                val name = truncate(category.name, valuePaint, PAGE_WIDTH_PT - MARGIN_PT * 2 - 120f)
                canvas.drawText(name, MARGIN_PT, y, labelPaint)
                val share = "${money(category.total)}  (${category.sharePercent.toPlainString()}%)"
                canvas.drawText(share, PAGE_WIDTH_PT - MARGIN_PT, y, valuePaint)
                y += SP * 1.4f
            }
            y += SP * 0.6f
        }

        // Top expenses.
        if (data.topExpenses.isNotEmpty()) {
            canvas.drawText("Top expenses", MARGIN_PT, y, paint(TEXT_BODY_SP, bold = true))
            y += SP * 1.5f
            data.topExpenses.forEach { expense ->
                val name = truncate(
                    expense.comment.ifBlank { "—" },
                    labelPaint,
                    PAGE_WIDTH_PT - MARGIN_PT * 2 - 160f,
                )
                canvas.drawText("· $name", MARGIN_PT, y, labelPaint)
                canvas.drawText(
                    "${money(expense.amount)}  ${expense.date}",
                    PAGE_WIDTH_PT - MARGIN_PT,
                    y,
                    valuePaint,
                )
                y += SP * 1.4f
            }
        }

        // Footer.
        val footerPaint = paint(TEXT_SMALL_SP, color = Color.GRAY)
        canvas.drawText(
            "Generated ${data.generatedAt.toLocalDate()} · MoneyPal",
            MARGIN_PT,
            PAGE_HEIGHT_PT - MARGIN_PT / 2,
            footerPaint,
        )
    }

    private fun paint(sizeSp: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizeSp * SP
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
            this.color = color
        }

    private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    companion object {
        private const val REPORTS_DIR = "reports"
        private const val PAGE_NUMBER = 1

        /** A4 in PostScript points (1pt = 1/72in). */
        private const val PAGE_WIDTH_PT = 595
        private const val PAGE_HEIGHT_PT = 842
        private const val MARGIN_PT = 48f

        /** Base density factor for SP → px on this canvas (1pt ≈ 1.33px). */
        private const val SP = 1.33f
        private const val TEXT_TITLE_SP = 20f
        private const val TEXT_BODY_SP = 11f
        private const val TEXT_SMALL_SP = 9f
    }
}
