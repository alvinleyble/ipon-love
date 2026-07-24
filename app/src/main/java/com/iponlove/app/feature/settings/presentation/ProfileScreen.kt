package com.iponlove.app.feature.settings.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.iponlove.app.core.ui.AccentColorRow
import com.iponlove.app.core.ui.MotifAvatar
import com.iponlove.app.core.ui.MotifPicker
import com.iponlove.app.core.ui.SettingsSectionHeader
import com.iponlove.app.core.ui.currencyGlyph
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.settings.domain.model.ResetFinancesCounts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalPlayfulColors.current
    val activity = LocalContext.current.findActivity()

    // Re-pull the account email + linked Google identity whenever Profile becomes visible (incl.
    // returning from the email client after a change-email link) so they update without a restart —
    // Item 8 + ADR-0051. ON_RESUME fires on first entry too, so this also covers the initial load.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAccount()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                    actionIconContentColor = colors.textSecondary,
                ),
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
            MotifAvatar(
                motifKey = state.avatarMotif,
                accentHex = state.accentColor,
                size = 72.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))

            SettingsSectionHeader("Nickname")
            Text(
                "This is what your partner sees in the combined view and on shared notes.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
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
                SettingsSectionHeader("Attribution color")
                Text(
                    "Your color in the combined view. Changes apply right away.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                AccentColorRow(
                    selectedHex = state.accentColor,
                    enabled = true,
                    onSelect = viewModel::onAccentColorSelected,
                    label = "",
                )
            }

            // Avatar motif is free for everyone (not couple-gated) — shown whether single or paired.
            Spacer(Modifier.height(28.dp))
            SettingsSectionHeader("Avatar")
            Text(
                "Pick a motif for your avatar. It's tinted by your accent color.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            MotifPicker(
                selectedKey = state.avatarMotif,
                accentHex = state.accentColor,
                onSelect = viewModel::onMotifSelected,
            )

            Spacer(Modifier.height(28.dp))
            SettingsSectionHeader("Account")
            Spacer(Modifier.height(4.dp))
            Text(
                state.email ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = viewModel::openChangePassword,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change password")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::openChangeEmail,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change email")
            }
            Spacer(Modifier.height(8.dp))
            ConnectGoogleRow(
                state = state,
                onConnect = { activity?.let(viewModel::connectGoogle) },
            )

            Spacer(Modifier.height(28.dp))
            SettingsSectionHeader("Restart fresh")
            Text(
                "Wipe your transaction history and reset every account balance to ${currencyGlyph()}0. Your " +
                    "accounts, categories, budgets, savings goals, and recurring bills are kept.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
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
            SettingsSectionHeader("Delete account")
            Text(
                "Permanently delete your account and all your data. This can't be undone.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
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

    if (state.showChangePasswordDialog) {
        ChangePasswordDialog(
            state = state,
            onCurrentChange = viewModel::onCurrentPasswordChange,
            onNewChange = viewModel::onNewPasswordChange,
            onConfirmChange = viewModel::onConfirmPasswordChange,
            onConfirm = viewModel::confirmChangePassword,
            onDismiss = viewModel::dismissChangePassword,
        )
    }

    if (state.showChangeEmailDialog) {
        ChangeEmailDialog(
            state = state,
            onPasswordChange = viewModel::onChangeEmailPasswordChange,
            onEmailChange = viewModel::onNewEmailChange,
            onConfirm = viewModel::confirmChangeEmail,
            onDismiss = viewModel::dismissChangeEmail,
        )
    }
}

/**
 * "Connect Google account" Account row (ADR-0051). State-driven: linked (incl. a Google-signup
 * user) renders a read-only Connected line with the Gmail; unlinked renders a full-width Connect
 * button matching its Change password / Change email siblings — no Google "G" mark, per decision 6
 * (section consistency over brand recognition for an in-app settings action).
 */
@Composable
private fun ConnectGoogleRow(
    state: ProfileUiState,
    onConnect: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    if (state.googleLinked) {
        Column {
            Text(
                "Google account",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                state.googleEmail ?: "Connected",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            Text(
                if (state.googleJustLinked) "Connected!" else "Connected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        OutlinedButton(
            onClick = onConnect,
            enabled = !state.isGoogleLinking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isGoogleLinking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text("Connect Google account")
            }
        }
        if (state.googleLinkError != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                state.googleLinkError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ChangePasswordDialog(
    state: ProfileUiState,
    onCurrentChange: (String) -> Unit,
    onNewChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.passwordChanged) "Password changed" else "Change password") },
        text = {
            if (state.passwordChanged) {
                Text(
                    "Your password has been updated. Use it the next time you sign in.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.currentPasswordInput,
                        onValueChange = onCurrentChange,
                        label = { Text("Current password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.newPasswordInput,
                        onValueChange = onNewChange,
                        label = { Text("New password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.confirmPasswordInput,
                        onValueChange = onConfirmChange,
                        label = { Text("Confirm new password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!state.isOnline) {
                        Text(
                            "You need an internet connection to change your password.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.changePasswordError != null) {
                        Text(
                            state.changePasswordError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.passwordChanged) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(onClick = onConfirm, enabled = state.canConfirmChangePassword) {
                    if (state.isChangePasswordLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Change password")
                    }
                }
            }
        },
        dismissButton = {
            if (!state.passwordChanged) {
                TextButton(onClick = onDismiss, enabled = !state.isChangePasswordLoading) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ChangeEmailDialog(
    state: ProfileUiState,
    onPasswordChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.emailChangeRequested) "Confirm your new email" else "Change email") },
        text = {
            if (state.emailChangeRequested) {
                Text(
                    "We've sent a confirmation link to ${state.newEmailInput.trim()}. Your email " +
                        "changes once you tap that link — until then you keep signing in with your " +
                        "current address.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter your new email and your current password to confirm.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = state.newEmailInput,
                        onValueChange = onEmailChange,
                        label = { Text("New email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.changeEmailPasswordInput,
                        onValueChange = onPasswordChange,
                        label = { Text("Current password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!state.isOnline) {
                        Text(
                            "You need an internet connection to change your email.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.changeEmailError != null) {
                        Text(
                            state.changeEmailError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.emailChangeRequested) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(onClick = onConfirm, enabled = state.canConfirmChangeEmail) {
                    if (state.isChangeEmailLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Send confirmation")
                    }
                }
            }
        },
        dismissButton = {
            if (!state.emailChangeRequested) {
                TextButton(onClick = onDismiss, enabled = !state.isChangeEmailLoading) { Text("Cancel") }
            }
        },
    )
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
                            "${counts.accounts} account balances to ${currencyGlyph()}0.",
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
