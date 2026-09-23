package com.sachit.moneypal.presentation.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sachit.moneypal.R
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.lock.AutoLockPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import logcat.logcat
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app-lock gate state (plan 009). Unlock is deliberately memory-only:
 * if the process dies the lock re-engages on next launch, which is the desired
 * behavior. Nothing about "unlocked" is ever persisted.
 *
 * The biometric prompt itself must be created with the foreground
 * [FragmentActivity], so this class only builds the prompt info and records
 * the outcome; the UI layer drives [authenticate] from composition.
 */
@Singleton
class AppLockController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val _isLocked = MutableStateFlow(false)

    /** True while the UI must be obscured behind the auth gate. */
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    /** When the host activity last paused (plan 041). Null until first pause. */
    @Volatile
    private var lastPausedAtEpochMs: Long? = null

    /** Host records its backgrounding so the timeout clock can run. */
    fun onHostPaused() {
        lastPausedAtEpochMs = System.currentTimeMillis()
    }

    /**
     * Re-evaluates the lock: engages the gate when the toggle is enabled AND
     * the configured re-lock delay has elapsed since the host last paused
     * (plan 041). Called at activity start and on every resume.
     */
    suspend fun refreshLock() {
        val settings = settingsRepository.getSettings()
        _isLocked.value = AutoLockPolicy.shouldLock(
            enabled = settings.appLockEnabled,
            timeout = settings.autoLockTimeout,
            lastPausedAtEpochMs = lastPausedAtEpochMs,
            nowEpochMs = System.currentTimeMillis(),
        )
    }

    /** Whether the device has a biometric or credential authenticator enrolled. */
    fun canAuthenticate(): Boolean {
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows the system auth prompt on [activity]. [onSuccess] runs on the main
     * executor when authentication succeeds; [onError] receives a localized
     * error string otherwise (user cancel included — treat as non-fatal).
     */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (CharSequence) -> Unit,
    ) {
        if (!canAuthenticate()) {
            logcat(TAG) { "authenticate: no authenticator enrolled" }
            onError(activity.getString(R.string.app_lock_no_authenticator))
            return
        }
        val executor: Executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    logcat(TAG) { "authenticate: succeeded" }
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    logcat(TAG) { "authenticate: error code=$errorCode" }
                    onError(errString)
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.app_lock_prompt_title))
            .setSubtitle(context.getString(R.string.app_lock_prompt_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(promptInfo)
    }

    fun unlock() {
        _isLocked.value = false
    }

    companion object {
        private const val TAG = "SACHIT:AppLock"
    }
}
