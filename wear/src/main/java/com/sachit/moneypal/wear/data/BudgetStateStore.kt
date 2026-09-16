package com.sachit.moneypal.wear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sachit.moneypal.sync.contract.BudgetStatePayload
import com.sachit.moneypal.sync.contract.WearJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

private val Context.budgetStateDataStore by preferencesDataStore(name = "wear_budget_state")

/**
 * Watch-side cache of the last phone-published [BudgetStatePayload] (plan 010).
 * The tile renders this; empty state means "set up on the phone".
 */
class BudgetStateStore(private val context: Context) {

    private val key = stringPreferencesKey("budget_state_json")

    val budgetState: Flow<BudgetStatePayload?> = context.budgetStateDataStore.data.map { prefs ->
        val raw = prefs[key]
        if (raw.isNullOrBlank()) {
            null
        } else {
            runCatching {
                WearJson.json.decodeFromString(BudgetStatePayload.serializer(), raw)
            }.getOrNull()
        }
    }

    suspend fun save(payload: BudgetStatePayload) {
        context.budgetStateDataStore.edit { prefs ->
            prefs[key] = WearJson.json.encodeToString(payload)
        }
    }

    suspend fun getAllOnce(): BudgetStatePayload? = budgetState.first()
}
