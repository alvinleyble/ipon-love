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
    val error: AuthError? = null,
    val confirmationSent: Boolean = false,
    // Raised when sign-out couldn't flush pending changes (offline): wiping local data would
    // lose them, so the UI confirms before proceeding (ADR-0021).
    val signOutPendingConfirm: Boolean = false,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting &&
            // A display name and a matching confirm-password entry are required to register,
            // but not to sign in (ADR-0016, ADR-0027 decision 4).
            (mode == AuthMode.SIGN_IN || (name.isNotBlank() && confirmPassword.isNotBlank()))
}
