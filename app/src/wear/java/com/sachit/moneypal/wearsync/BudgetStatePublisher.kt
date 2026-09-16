package com.sachit.moneypal.wearsync

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.sync.contract.BudgetStatePayload
import com.sachit.moneypal.sync.contract.WearJson
import com.sachit.moneypal.sync.contract.WearPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import logcat.asLog
import logcat.logcat
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes the current [BudgetStatePayload] to the watch (plan 010) so the
 * budget tile can render it. Absence of a watch must never throw on the
 * phone: every send is best-effort and swallowed.
 */
@Singleton
class BudgetStatePublisher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val budgetRepository: BudgetRepository,
) {

    /**
     * Computes the current budget state and sends it to every reachable phone
     * receiver node. Returns true when at least one send succeeded.
     */
    suspend fun publishNow(): Boolean {
        val settings = budgetRepository.getBudgetSettingsSync() ?: return false
        val today = LocalDate.now()
        val state = budgetRepository
            .calculateBudgetState(settings, today)
            .first()
        val payload = BudgetStatePayload(
            remainingToday = state.remainingToday.toPlainString(),
            dailyBudget = state.dailyBudget.toPlainString(),
            currencyCode = settings.currencyCode,
            progressPercent = (state.progress * 100).toInt(),
            daysRemaining = state.daysRemaining,
            isOverBudget = state.isOverBudget,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        return send(payload)
    }

    /** Best-effort push of [payload] to all reachable receiver nodes. */
    suspend fun send(payload: BudgetStatePayload): Boolean = runCatching {
        val bytes = WearJson.json.encodeToString(payload).encodeToByteArray()
        val nodes = Wearable.getCapabilityClient(context)
            .getCapability(RECEIVER_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .toList()
        if (nodes.isEmpty()) {
            logcat { "BudgetStatePublisher: no receiver nodes — skipping publish" }
            return@runCatching false
        }
        val messageClient = Wearable.getMessageClient(context)
        var sentAny = false
        for (node in nodes) {
            runCatching {
                messageClient.sendMessage(node.id, WearPaths.BUDGET_STATE, bytes).await()
            }.onSuccess {
                sentAny = true
            }.onFailure { e ->
                logcat { "BudgetStatePublisher: send failed for node=${node.id}\n${e.asLog()}" }
            }
        }
        sentAny
    }.getOrElse { e ->
        logcat { "BudgetStatePublisher: publish failed\n${e.asLog()}" }
        false
    }

    companion object {
        private const val RECEIVER_CAPABILITY = "minus_phone_receiver"
    }
}
