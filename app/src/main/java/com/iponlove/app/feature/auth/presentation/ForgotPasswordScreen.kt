package com.iponlove.app.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.onPlayfulSurface
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

@Composable
fun ForgotPasswordScreen(viewModel: ForgotPasswordViewModel, onBack: () -> Unit) {
    val state by viewModel.form.collectAsState()
    ForgotPasswordContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onSubmit = viewModel::submit,
        onBack = onBack,
    )
}

@Composable
private fun ForgotPasswordContent(
    state: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    Box(modifier = Modifier.fillMaxSize().playfulBackground()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Reset your password",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Enter your account email and we'll send you a link to set a new password.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            if (state.emailSent) {
                ResetEmailSentBanner()
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                enabled = !state.emailSent,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
                isError = state.error != null,
                supportingText = state.error?.let { error -> { Text(error.message()) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            if (!state.emailSent) {
                Button(
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Send reset link")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            TextButton(onClick = onBack) {
                Text("Back to sign in")
            }
        }
    }
}

@Composable
private fun ResetEmailSentBanner() {
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Blush,
        shape = LeafShapes.Card,
    ) {
        Text(
            text = "Check your email for a link to reset your password.",
            style = MaterialTheme.typography.bodyMedium,
            color = onPlayfulSurface(PlayfulSurface.Blush),
            textAlign = TextAlign.Center,
        )
    }
}
