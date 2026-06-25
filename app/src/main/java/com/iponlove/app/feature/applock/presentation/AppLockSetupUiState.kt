package com.iponlove.app.feature.applock.presentation

data class AppLockSetupUiState(
    val step: SetupStep = SetupStep.ENTER_NEW,
    val newPin: String = "",
    val confirmPin: String = "",
    val error: String? = null,
    val pinSetSuccess: Boolean = false,
)

enum class SetupStep { ENTER_NEW, CONFIRM }
