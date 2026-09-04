package com.sachit.moneypal.wear.sync

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import logcat.logcat
import com.sachit.moneypal.wear.data.CategorySuggestionStore
import com.sachit.moneypal.wear.data.PendingExpenseStore
import com.sachit.moneypal.sync.contract.AckPayload
import com.sachit.moneypal.sync.contract.AckStatus
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
}
