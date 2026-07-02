package com.iponlove.app.feature.applock.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSetupScreen(
    onBack: () -> Unit,
    viewModel: AppLockSetupViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showBiometricNudge by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.pinSetSuccess) {
        if (uiState.pinSetSuccess) {
            // Item 13: one-time nudge to enable biometric, only when the device can actually use it
            // (hardware present + credential enrolled), the app's biometric preference is still off,
            // and the nudge hasn't been shown before. Read before resetPinSetSuccess() clears state.
            if (BiometricAvailability.isReady(context) &&
                !prefs.isBiometricEnabled &&
                !prefs.biometricNudgeShown
            ) {
                showBiometricNudge = true
            }
            viewModel.resetPinSetSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (prefs.isPinSet) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("PIN lock", style = MaterialTheme.typography.bodyLarge)
                        Text("Enabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Use biometric", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = prefs.isBiometricEnabled, onCheckedChange = viewModel::onBiometricToggle)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                Text(
                    if (uiState.step == SetupStep.ENTER_NEW) "Change PIN" else "Confirm new PIN",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "Set a 4-digit PIN to lock the app when it goes to the background.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    if (uiState.step == SetupStep.ENTER_NEW) "Enter PIN" else "Confirm PIN",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            PinDots(
                length = when (uiState.step) {
                    SetupStep.ENTER_NEW -> uiState.newPin.length
                    SetupStep.CONFIRM -> uiState.confirmPin.length
                },
            )
            if (uiState.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(24.dp))
            Numpad(onDigit = viewModel::onDigit, onDelete = viewModel::onDelete)
        }
    }

    if (showBiometricNudge) {
        AlertDialog(
            onDismissRequest = {
                viewModel.markBiometricNudgeShown()
                showBiometricNudge = false
            },
            icon = { Icon(Icons.Filled.Fingerprint, contentDescription = null) },
            title = { Text("Unlock with biometrics?") },
            text = {
                Text(
                    "Use your fingerprint or face to unlock the app faster. Your PIN still works " +
                        "as a backup any time.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onBiometricToggle(true)
                    viewModel.markBiometricNudgeShown()
                    showBiometricNudge = false
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.markBiometricNudgeShown()
                    showBiometricNudge = false
                }) { Text("Not now") }
            },
        )
    }
}
