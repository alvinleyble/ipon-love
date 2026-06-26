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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    LaunchedEffect(isBiometricEnabled) {
        if (isBiometricEnabled) {
            val activity = context as? FragmentActivity ?: return@LaunchedEffect
            showBiometricPrompt(activity, onSuccess = viewModel::onBiometricSuccess)
        }
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
                    "Enter your PIN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PinDots(length = uiState.pin.length)
                Spacer(Modifier.height(8.dp))
                if (uiState.error != null) {
                    Text(
                        uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 48.dp),
            ) {
                Numpad(onDigit = viewModel::onDigit, onDelete = viewModel::onDelete)
                Spacer(Modifier.height(16.dp))
                if (isBiometricEnabled) {
                    TextButton(onClick = {
                        val activity = context as? FragmentActivity ?: return@TextButton
                        showBiometricPrompt(activity, onSuccess = viewModel::onBiometricSuccess)
                    }) {
                        Text("Use biometric")
                    }
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
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
            onSuccess()
    }
    BiometricPrompt(activity, executor, callback).authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock ${activity.getString(R.string.app_name)}")
            .setNegativeButtonText("Use PIN")
            .build(),
    )
}
