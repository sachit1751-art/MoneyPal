package com.sachit.moneypal.presentation.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.sachit.moneypal.R

/**
 * Full-screen gate drawn on top of the NavHost while [AppLockController.isLocked]
 * is true (plan 009). The unlock button triggers the system biometric/credential
 * prompt; auth errors render inline (user-cancel included, non-fatal).
 *
 * [activity] must be the foreground [FragmentActivity] (MainActivity is an
 * AppCompatActivity, which extends FragmentActivity).
 */
@Composable
fun AppLockOverlay(
    controller: AppLockController,
    activity: FragmentActivity,
    modifier: Modifier = Modifier,
) {
    val authError = remember { mutableStateOf<String?>(null) }

    // While locked, the gate must be a hard barrier: swallow back presses and
    // consume taps/drag hit-testing so they never reach the NavHost underneath.
    BackHandler(enabled = true) { /* locked — no-op */ }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { /* consume taps over the whole overlay */ }
            },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_lock_overlay_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = {
                controller.authenticate(
                    activity = activity,
                    onSuccess = { controller.unlock() },
                    onError = { message -> authError.value = message.toString() },
                )
            }) {
                Text(text = stringResource(R.string.app_lock_unlock_button))
            }
            authError.value?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
