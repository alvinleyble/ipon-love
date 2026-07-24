package com.iponlove.app.feature.auth.presentation

import com.iponlove.app.feature.auth.domain.model.AuthError

/** Whether the form is registering a new account or signing into an existing one. */
enum class AuthMode { SIGN_IN, SIGN_UP }

/**
 * Auth form state. [confirmationSent] is shown after a sign-up that needs email
 * confirmation — the form flips back to [AuthMode.SIGN_IN] with this banner up.
 */
data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    // Independent of [isSubmitting] so the Google button spins without disabling/spinning the
    // email button, and vice-versa (ADR-0050 decision 6).
    val isGoogleSubmitting: Boolean = false,
    val error: AuthError? = null,
    val confirmationSent: Boolean = false,
    // Raised when sign-out couldn't flush pending changes (offline): wiping local data would
    // lose them, so the UI confirms before proceeding (ADR-0021).
    val signOutPendingConfirm: Boolean = false,
    // Sign-in lockout (Item 17): consecutive failed sign-ins; when > 0 the button is blocked with
    // a countdown, mirroring the PIN lockout. Both reset on a successful sign-in.
    val failedSignInAttempts: Int = 0,
    val signInLockoutSeconds: Long = 0,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting &&
            signInLockoutSeconds == 0L &&
            // A display name and a matching confirm-password entry are required to register,
            // but not to sign in (ADR-0016, ADR-0027 decision 4).
            (mode == AuthMode.SIGN_IN || (name.isNotBlank() && confirmPassword.isNotBlank()))
}
