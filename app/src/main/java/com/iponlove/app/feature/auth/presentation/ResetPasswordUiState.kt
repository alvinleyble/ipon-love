package com.iponlove.app.feature.auth.presentation

import com.iponlove.app.feature.auth.domain.model.AuthError

/** "Set new password" form state, shown only for an [com.iponlove.app.feature.auth.domain.model.AuthStatus.PasswordRecovery] session. */
data class ResetPasswordUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
) {
    val canSubmit: Boolean
        get() = password.isNotBlank() && confirmPassword.isNotBlank() && !isSubmitting
}
