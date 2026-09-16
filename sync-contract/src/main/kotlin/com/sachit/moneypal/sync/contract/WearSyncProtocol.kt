package com.sachit.moneypal.sync.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object WearPaths {
    const val EXPENSE_ADD = "/expense/add"
    const val EXPENSE_ACK = "/expense/ack"
    const val EXPENSE_SNAPSHOT = "/expense/snapshot"
    const val EXPENSE_SNAPSHOT_RESPONSE = "/expense/snapshot/response"
    const val BUDGET_STATE = "/budget/state"
    const val BUDGET_STATE_REQUEST = "/budget/state/request"
}

object WearJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

@Serializable
data class ExpensePayload(
    val clientGeneratedId: String,
    val amount: String,
    val comment: String,
    val eventTime: Long,
    val periodId: Long? = null
)

@Serializable
enum class AckStatus {
    OK,
    ERROR
}

@Serializable
data class AckPayload(
    val clientGeneratedId: String,
    val status: AckStatus,
    val reason: String? = null
)

@Serializable
data class SnapshotRequestPayload(
    val limit: Int = 20
)

@Serializable
data class SnapshotExpenseItem(
    val clientGeneratedId: String,
    val amount: String,
    val comment: String,
    val eventTime: Long
)

@Serializable
data class SnapshotResponsePayload(
    val items: List<SnapshotExpenseItem>
)

/**
 * Phone → watch budget state for the watch tile (plan 010). Money amounts are
 * plain strings (money never floats — see the backup codec convention).
 */
@Serializable
data class BudgetStatePayload(
    /** Remaining spendable today, formatted for display. */
    val remainingToday: String,
    /** Daily budget, formatted for display. */
    val dailyBudget: String,
    val currencyCode: String,
    /** 0..100+ percent of the period budget consumed. */
    val progressPercent: Int,
    val daysRemaining: Int,
    val isOverBudget: Boolean,
    /** Epoch millis the phone computed this state — powers the freshness rule. */
    val updatedAtEpochMs: Long,
)
