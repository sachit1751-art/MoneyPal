package com.sachit.moneypal.presentation.report

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt entry point for the plan-044 report pipeline (same pattern as CsvTransferEntryPoint). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MonthlyReportEntryPoint {
    fun monthlyReportPdfWriter(): MonthlyReportPdfWriter
    fun monthlyReportShareManager(): MonthlyReportShareManager
}
