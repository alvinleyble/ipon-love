package com.iponlove.app.feature.applock.domain.model

import java.time.Instant

/**
 * Flat-threshold PIN lockout (ADR-0028): after [MAX_ATTEMPTS] consecutive wrong PINs, block
 * further attempts for [COOLDOWN_SECONDS]. Deliberately flat, not progressive — this PIN only
 * gates local access to an already-authenticated session, not a payment.
 */
object PinLockoutPolicy {
    const val MAX_ATTEMPTS = 5
    const val COOLDOWN_SECONDS = 30L

    fun remainingLockoutSeconds(prefs: AppLockPreferences, now: Instant): Long {
        val until = prefs.pinLockoutUntil ?: return 0
        return (until.epochSecond - now.epochSecond).coerceAtLeast(0)
    }

    fun isLockedOut(prefs: AppLockPreferences, now: Instant): Boolean =
        remainingLockoutSeconds(prefs, now) > 0

    /** Call once a wrong PIN has already been confirmed (i.e. never while still locked out). */
    fun onFailure(prefs: AppLockPreferences, now: Instant): AppLockPreferences {
        val attempts = prefs.failedPinAttempts + 1
        val lockoutUntil = if (attempts >= MAX_ATTEMPTS) now.plusSeconds(COOLDOWN_SECONDS) else null
        return prefs.copy(failedPinAttempts = attempts, pinLockoutUntil = lockoutUntil)
    }

    fun onSuccess(prefs: AppLockPreferences): AppLockPreferences =
        prefs.copy(failedPinAttempts = 0, pinLockoutUntil = null)
}
