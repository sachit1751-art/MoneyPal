package com.sachit.moneypal.wear.sync

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import logcat.logcat
import com.sachit.moneypal.wear.data.BudgetStateStore
import com.sachit.moneypal.wear.data.CategorySuggestionStore
import com.sachit.moneypal.wear.data.PendingExpenseStore
import com.sachit.moneypal.wear.presentation.BudgetTileService
import com.sachit.moneypal.sync.contract.AckPayload
import com.sachit.moneypal.sync.contract.AckStatus
import com.sachit.moneypal.sync.contract.BudgetStatePayload
import com.sachit.moneypal.sync.contract.SnapshotResponsePayload
import com.sachit.moneypal.sync.contract.WearJson
import com.sachit.moneypal.sync.contract.WearPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WearWatchListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearPaths.EXPENSE_ACK -> handleAck(messageEvent.data)
            WearPaths.EXPENSE_SNAPSHOT_RESPONSE -> handleSnapshotResponse(messageEvent.data)
            WearPaths.BUDGET_STATE -> handleBudgetState(messageEvent.data)
            else -> super.onMessageReceived(messageEvent)
        }
    }

    private fun handleAck(data: ByteArray) {
        val ack = runCatching {
            WearJson.json.decodeFromString<AckPayload>(data.decodeToString())
        }.getOrNull() ?: return

        val store = PendingExpenseStore(applicationContext)
        scope.launch {
            if (ack.status == AckStatus.OK) {
                store.markSynced(ack.clientGeneratedId)
            } else {
                store.markFailedRetryable(ack.clientGeneratedId)
            }
        }
    }

    private fun handleSnapshotResponse(data: ByteArray) {
        val payload = runCatching {
            WearJson.json.decodeFromString<SnapshotResponsePayload>(data.decodeToString())
        }.getOrNull() ?: return

        val comments = payload.items.map { it.comment }
        scope.launch {
            CategorySuggestionStore(applicationContext).saveFromComments(comments)
            logcat { "handleSnapshotResponse: cached categories=${comments.size}" }
        }
    }

    /** Caches phone-published budget state and refreshes the tile (plan 010). */
    private fun handleBudgetState(data: ByteArray) {
        val payload = runCatching {
            WearJson.json.decodeFromString(BudgetStatePayload.serializer(), data.decodeToString())
        }.getOrNull()

        if (payload == null) {
            logcat { "handleBudgetState: undecodable payload — dropped" }
            return
        }

        scope.launch {
            BudgetStateStore(applicationContext).save(payload)
            logcat { "handleBudgetState: cached state, remainingToday=${payload.remainingToday}" }
            requestTileRefresh()
        }
    }

    private fun requestTileRefresh() {
        runCatching {
            TileService.getUpdater(applicationContext)
                .requestUpdate(BudgetTileService::class.java)
        }.onFailure { e ->
            logcat { "requestTileRefresh: failed (tile may not be added yet)\n${e.message}" }
        }
    }
}
