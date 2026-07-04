package com.iponlove.app.feature.applock.domain.model

import java.time.Instant

data class AppLockPreferences(
    val isPinSet: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    // True once the post-PIN-setup "enable biometric?" nudge has been shown, so it never nags
    // again (item 13). Reset when the PIN is cleared so a fresh setup can re-offer it.
    val biometricNudgeShown: Boolean = false,
    // PIN lockout (item 8, ADR-0028). Persists across process death and resets only on a
    // successful unlock — never on elapsed idle time, or a brute-forcer could just wait it out.
    val failedPinAttempts: Int = 0,
    val pinLockoutUntil: Instant? = null,
)
