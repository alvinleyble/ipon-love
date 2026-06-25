package com.iponlove.app.feature.applock.domain.model

data class AppLockPreferences(
    val isPinSet: Boolean = false,
    val isBiometricEnabled: Boolean = false,
)
