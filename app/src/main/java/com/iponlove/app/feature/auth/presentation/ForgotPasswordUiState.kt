package com.iponlove.app.feature.auth.presentation

import com.iponlove.app.feature.auth.domain.model.AuthError

/**
 * Request-a-reset-email form state. [emailSent] flips to true on success — the screen then
 * shows a "check your email" banner rather than navigating anywhere (ADR-0027).
 */
data class ForgotPasswordUiState(
    val email: String = "",
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
    val emailSent: Boolean = false,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && !isSubmitting
}
