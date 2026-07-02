package com.iponlove.app.feature.applock.domain.model

data class AppLockPreferences(
    val isPinSet: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    // True once the post-PIN-setup "enable biometric?" nudge has been shown, so it never nags
    // again (item 13). Reset when the PIN is cleared so a fresh setup can re-offer it.
    val biometricNudgeShown: Boolean = false,
)
