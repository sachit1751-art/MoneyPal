package com.sachit.moneypal.presentation.ui.budget

import com.sachit.moneypal.domain.calculator.evaluateExpression
import javax.inject.Inject

class BudgetExpressionEvaluator @Inject constructor() {

    fun evaluate(input: String): String? = evaluateExpression(input)
}
