package com.sachit.moneypal.presentation.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sachit.moneypal.R
import com.sachit.moneypal.data.repository.SettingsRepository
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

    /**
     * Re-evaluates the lock: engages the gate when the user has the toggle
     * enabled. Called at activity start; call again from ON_START to re-lock
     * after returning from the background (future enhancement, not wired yet).
     */
    suspend fun refreshLock() {
        _isLocked.value = settingsRepository.getSettings().appLockEnabled
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
