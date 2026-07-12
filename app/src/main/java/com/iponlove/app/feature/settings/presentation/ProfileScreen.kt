package com.iponlove.app.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.AccentColorRow
import com.iponlove.app.feature.settings.domain.model.ResetFinancesCounts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text("Nickname", style = MaterialTheme.typography.titleMedium)
            Text(
                "This is what your partner sees in the combined view and on shared notes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.nameDraft,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nickname") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::saveName,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.saved) "Saved!" else "Save nickname")
            }

            // The attribution color only means anything once there's a partner to attribute
            // against, so it's hidden when single (ADR-0016).
            if (state.isPaired) {
                Spacer(Modifier.height(28.dp))
                Text("Attribution color", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your color in the combined view. Changes apply right away.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                AccentColorRow(
                    selectedHex = state.accentColor,
                    enabled = true,
                    onSelect = viewModel::onAccentColorSelected,
                    label = "",
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("Account", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                state.email ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))
            Text("Restart fresh", style = MaterialTheme.typography.titleMedium)
            Text(
                "Wipe your transaction history and reset every account balance to ₱0. Your " +
                    "accounts, categories, budgets, savings goals, and recurring bills are kept.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = viewModel::openResetFinances,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.financesJustReset) "Finances reset" else "Reset finances")
            }

            Spacer(Modifier.height(28.dp))
            Text("Delete account", style = MaterialTheme.typography.titleMedium)
            Text(
                "Permanently delete your account and all your data. This can't be undone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = viewModel::openDeleteAccount,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete account")
            }
        }
    }

    if (state.showResetFinancesDialog) {
        ResetFinancesDialog(
            counts = state.resetFinancesCounts,
            password = state.resetFinancesPassword,
            isLoading = state.isResetFinancesLoading,
            isOnline = state.isOnline,
            canConfirm = state.canConfirmReset,
            error = state.resetFinancesError,
            onPasswordChange = viewModel::onResetFinancesPasswordChange,
            onConfirm = viewModel::confirmResetFinances,
            onDismiss = viewModel::dismissResetFinances,
        )
    }

    if (state.showDeleteAccountDialog) {
        DeleteAccountDialog(
            isPaired = state.isPaired,
            password = state.deleteAccountPassword,
            isLoading = state.isDeleteAccountLoading,
            isOnline = state.isOnline,
            canConfirm = state.canConfirmDelete,
            error = state.deleteAccountError,
            onPasswordChange = viewModel::onDeleteAccountPasswordChange,
            onConfirm = viewModel::confirmDeleteAccount,
            onDismiss = viewModel::dismissDeleteAccount,
        )
    }
}

@Composable
private fun ResetFinancesDialog(
    counts: ResetFinancesCounts?,
    password: String,
    isLoading: Boolean,
    isOnline: Boolean,
    canConfirm: Boolean,
    error: String?,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset finances?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (counts == null) {
                    Text(
                        "Checking what would change…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "This permanently deletes ${counts.transactions} transactions and sets " +
                            "${counts.accounts} account balances to ₱0.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Everything else — accounts, categories, budgets, savings goals, and " +
                            "recurring bills — is kept.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Enter your password to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isOnline) {
                    Text(
                        "You need an internet connection to reset.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
            TextButton(
                onClick = onConfirm,
                enabled = canConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Reset finances")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteAccountDialog(
    isPaired: Boolean,
    password: String,
    isLoading: Boolean,
    isOnline: Boolean,
    canConfirm: Boolean,
    error: String?,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This permanently deletes your account and all your data — transactions, " +
                        "accounts, budgets, notes, and photos. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isPaired) {
                    Text(
                        "You'll be unpaired from your partner first. Their own data is untouched.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Enter your password to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isOnline) {
                    Text(
                        "You need an internet connection to delete your account.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
            TextButton(
                onClick = onConfirm,
                enabled = canConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Delete account")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") }
        },
    )
}
