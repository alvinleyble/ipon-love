package com.iponlove.app.feature.applock.presentation

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.iponlove.app.R
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LockScreen(
    isBiometricEnabled: Boolean,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // The PIN pad is the permanent fallback and is NEVER removed. When biometric is enabled the
    // lock opens in "biometric mode" — the pad is visually deferred behind the system biometric
    // prompt — but every non-success biometric outcome flips this true so the user is never locked
    // out of their own app (ADR-0023). rememberSaveable keeps the user on the pad across a rotation
    // once they've fallen back to it.
    var showPinPad by rememberSaveable { mutableStateOf(!isBiometricEnabled) }
    // Distinct from the PIN's own lockout message below — this one is set only when the OS itself
    // reports ERROR_LOCKOUT/ERROR_LOCKOUT_PERMANENT (item 8), instead of silently falling back to
    // the PIN pad with no explanation.
    var biometricLockoutMessage by rememberSaveable { mutableStateOf<String?>(null) }

    fun launchBiometric() {
        val activity = context as? FragmentActivity ?: run { showPinPad = true; return }
        showBiometricPrompt(
            activity,
            onSuccess = viewModel::onBiometricSuccess,
            onFallbackToPin = { lockoutMessage ->
                showPinPad = true
                biometricLockoutMessage = lockoutMessage
            },
        )
    }

    // Auto-prompt once on entering biometric mode. We deliberately do NOT re-prompt from here after
    // a fallback — re-arming automatically would create a cancel→prompt→cancel loop; the user
    // re-triggers biometric explicitly via the "Use biometric" button.
    LaunchedEffect(Unit) {
        if (isBiometricEnabled && !showPinPad) launchBiometric()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(80.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (showPinPad) "Enter your PIN" else "Unlock to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (showPinPad) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PinDots(length = uiState.pin.length)
                    Spacer(Modifier.height(8.dp))
                    val message = uiState.error ?: biometricLockoutMessage
                    if (message != null) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 48.dp),
            ) {
                if (showPinPad) {
                    Numpad(
                        onDigit = { digit -> biometricLockoutMessage = null; viewModel.onDigit(digit) },
                        onDelete = viewModel::onDelete,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (isBiometricEnabled) {
                        TextButton(onClick = { launchBiometric() }) {
                            Text("Use biometric")
                        }
                    }
                } else {
                    Button(onClick = { showPinPad = true }) {
                        Text("Use PIN instead")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = viewModel::showForgotPin) {
                    Text("Forgot PIN")
                }
            }
        }
    }

    if (uiState.showForgotPinDialog) {
        ForgotPinDialog(
            email = uiState.forgotPinEmail,
            password = uiState.forgotPinPassword,
            isLoading = uiState.isForgotPinLoading,
            error = uiState.forgotPinError,
            onEmailChange = viewModel::onForgotPinEmailChange,
            onPasswordChange = viewModel::onForgotPinPasswordChange,
            onConfirm = viewModel::submitForgotPin,
            onDismiss = viewModel::dismissForgotPin,
        )
    }
}

@Composable
private fun ForgotPinDialog(
    email: String,
    password: String,
    isLoading: Boolean,
    error: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify your identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter your email and password to reset your PIN.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Verify & Reset PIN")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onFallbackToPin: (lockoutMessage: String?) -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        // Every non-success terminal outcome — negative button ("Use PIN"), user cancel, hardware
        // error, ERROR_LOCKOUT / ERROR_LOCKOUT_PERMANENT — must surface the PIN pad. Biometric is a
        // convenience layer over the PIN, never a replacement; wiring only onAuthenticationSucceeded
        // (the old behaviour) would lock the user out on any biometric failure (ADR-0023). The OS's
        // own lockout codes (item 8) get a distinct message instead of silently falling back.
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val lockoutMessage = if (
                errorCode == BiometricPrompt.ERROR_LOCKOUT || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT
            ) {
                "Biometric temporarily locked out — use your PIN"
            } else {
                null
            }
            onFallbackToPin(lockoutMessage)
        }

        // A single unrecognized fingerprint leaves the system prompt open for more tries; surfacing
        // the PIN pad underneath is harmless and keeps the fallback one tap (the negative button)
        // away.
        override fun onAuthenticationFailed() {
            onFallbackToPin(null)
        }
    }
    BiometricPrompt(activity, executor, callback).authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock ${activity.getString(R.string.app_name)}")
            .setNegativeButtonText("Use PIN")
            .build(),
    )
}
