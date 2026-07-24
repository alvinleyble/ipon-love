package com.iponlove.app.feature.auth.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iponlove.app.R
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.onPlayfulSurface
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.auth.domain.model.AuthError

@Composable
fun AuthScreen(viewModel: AuthViewModel, onForgotPassword: () -> Unit) {
    val state by viewModel.form.collectAsState()
    // Credential Manager needs an Activity context (not the application context) to host its UI.
    val activity = LocalContext.current.findActivity()
    AuthContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onSubmit = viewModel::submit,
        onToggleMode = viewModel::toggleMode,
        onForgotPassword = onForgotPassword,
        onGoogleSignIn = { activity?.let(viewModel::signInWithGoogle) },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun AuthContent(
    state: AuthUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit,
    onForgotPassword: () -> Unit,
    onGoogleSignIn: () -> Unit,
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
            Image(
                painter = painterResource(R.drawable.ic_heart_wallet),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Love, Ipon",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.mode == AuthMode.SIGN_IN) "Welcome back" else "Create your account",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(32.dp))

            if (state.confirmationSent) {
                ConfirmationBanner()
                Spacer(Modifier.height(16.dp))
            }

            if (state.mode == AuthMode.SIGN_UP) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Your nickname") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                isError = state.error != null,
                supportingText = state.error?.let { error -> { Text(error.message()) } },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.mode == AuthMode.SIGN_UP) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.mode == AuthMode.SIGN_IN) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onForgotPassword, enabled = !state.isSubmitting) {
                    Text("Forgot password?")
                }
            }
            Spacer(Modifier.height(20.dp))

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
                    Text(if (state.mode == AuthMode.SIGN_IN) "Sign in" else "Create account")
                }
            }
            if (state.signInLockoutSeconds > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Too many attempts. Try again in ${state.signInLockoutSeconds}s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            OrDivider()
            Spacer(Modifier.height(16.dp))
            // Same button in both modes — the tap creates, signs in, or links identically; the
            // SDK session flip drives the gate (ADR-0050 decision 5).
            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !state.isGoogleSubmitting && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isGoogleSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_google_g),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Continue with Google")
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onToggleMode, enabled = !state.isSubmitting) {
                Text(
                    if (state.mode == AuthMode.SIGN_IN) {
                        "Don't have an account? Sign up"
                    } else {
                        "Already have an account? Sign in"
                    },
                )
            }
        }
    }
}

@Composable
private fun OrDivider() {
    val colors = LocalPlayfulColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.textSecondary.copy(alpha = 0.3f))
        Text(
            text = "or",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.textSecondary.copy(alpha = 0.3f))
    }
}

@Composable
private fun ConfirmationBanner() {
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Blush,
        shape = LeafShapes.Card,
    ) {
        Text(
            text = "Account created. Check your email for a confirmation link, then sign in.",
            style = MaterialTheme.typography.bodyMedium,
            color = onPlayfulSurface(PlayfulSurface.Blush),
            textAlign = TextAlign.Center,
        )
    }
}

internal fun AuthError.message(): String = when (this) {
    AuthError.INVALID_CREDENTIALS -> "Incorrect email or password"
    AuthError.EMAIL_NOT_CONFIRMED -> "Confirm your email first — check your inbox"
    AuthError.EMAIL_ALREADY_REGISTERED -> "That email is already registered — sign in instead"
    AuthError.WEAK_PASSWORD ->
        "Password must be at least 6 characters, with uppercase, lowercase, a number, and a symbol"
    AuthError.INVALID_EMAIL -> "Enter a valid email address"
    AuthError.INVALID_NAME -> "Enter a nickname (letters only, up to 10 characters)"
    AuthError.PASSWORD_MISMATCH -> "Passwords don't match"
    AuthError.SAME_AS_OLD_PASSWORD -> "New password must be different from your current password"
    AuthError.RATE_LIMITED -> "Please wait a bit before making another request"
    AuthError.GOOGLE_NO_ACCOUNT -> "No Google account found on this device — add one in Settings first"
    AuthError.GOOGLE_SIGN_IN_FAILED -> "Couldn't sign in with Google. Please try again"
    AuthError.GOOGLE_ALREADY_LINKED -> "That Google account is already connected to another Love, Ipon account"
    AuthError.GOOGLE_LINK_FAILED -> "Couldn't connect your Google account. Please try again"
    AuthError.NETWORK -> "Can't reach the server — check your connection"
    AuthError.UNKNOWN -> "Something went wrong. Please try again"
}
