package com.sachit.moneypal.presentation.notification

import androidx.core.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sachit.moneypal.data.repository.BudgetRepository
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.model.PaymentMethod
import com.sachit.moneypal.domain.model.Transaction
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.logcat
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Direct-reply quick-add on budget notifications (plan 005): the user replies
 * "12.5 groceries" (or just "12.5") to a period/recurrent/credit notification
 * and the expense is logged into the current period. Respects the
 * past-period-end queueing rule via [QuickAddExpenseWriter].
 */
class QuickAddReceiver : BroadcastReceiver() {

    companion object {
        const val KEY_QUICK_ADD_TEXT = "quick_add_text"
        const val ACTION_QUICK_ADD = "com.sachit.moneypal.action.QUICK_ADD"

        /**
         * Parses a reply into (amount, category-comment). Rules: first token
         * is the amount (dot or comma decimal separator), the remainder is the
         * category/comment. Returns null when the amount can't be parsed.
         */
        fun parseReply(text: String): Pair<BigDecimal, String>? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            val normalized = parts[0].replace(',', '.')
            val amount = normalized.toBigDecimalOrNull() ?: return null
            if (amount.signum() <= 0) return null
            val comment = parts.getOrNull(1)?.trim().orEmpty()
            return amount to comment
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface QuickAddEntryPoint {
        fun quickAddExpenseWriter(): QuickAddExpenseWriter
        fun quickAddNotifier(): QuickAddNotifier
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_QUICK_ADD) return
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val text = results.getCharSequence(KEY_QUICK_ADD_TEXT)?.toString().orEmpty()
        val parsed = parseReply(text)
        if (parsed == null) {
            QuickAddNotifier.showError(context)
            return
        }
        val (amount, comment) = parsed
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    QuickAddEntryPoint::class.java,
                )
                val result = entryPoint.quickAddExpenseWriter().quickAdd(amount, comment)
                entryPoint.quickAddNotifier().showResult(amount, comment, result)
            } catch (e: Exception) {
                logcat { "QuickAdd failed: ${e.message}" }
                QuickAddNotifier.showError(context)
            } finally {
                pending.finish()
            }
        }
    }
}
