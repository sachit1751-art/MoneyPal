package com.sachit.moneypal.presentation.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.sachit.moneypal.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/**
 * Notification "Mute sender" action for a captured bank SMS (plan 048): adds
 * the sender to the mute list and dismisses the capture notification. Future
 * SMS from this sender are skipped before parsing by [ProcessIncomingSmsUseCase].
 */
@AndroidEntryPoint
class MuteSmsSenderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MUTE) return

        val sender = intent.getStringExtra(EXTRA_SENDER).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        if (sender.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val current = settingsRepository.getSettings().mutedSmsSenders
                settingsRepository.setMutedSmsSenders(current + sender)
                logcat(TAG) { "Muted SMS sender from notification: $sender" }
            } catch (e: Exception) {
                logcat(TAG) { "Mute failed for $sender: ${e.message}" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SACHIT:SmsMute"

        const val ACTION_MUTE = "com.sachit.moneypal.action.MUTE_SMS_SENDER"
        const val EXTRA_SENDER = "extra_sms_sender"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
