package com.sachit.moneypal.domain.model

import java.time.LocalDate

data class PeriodKey(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val period: BudgetPeriod
)
