package com.iponlove.app.feature.applock.presentation

data class AppLockUiState(
    val pin: String = "",
    val error: String? = null,
    val lockoutRemainingSeconds: Long = 0,
    val showForgotPinDialog: Boolean = false,
    val isForgotPinLoading: Boolean = false,
    val forgotPinEmail: String = "",
    val forgotPinPassword: String = "",
    val forgotPinError: String? = null,
)
