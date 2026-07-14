package com.iponlove.app.feature.auth.domain.model

/**
 * Flat-threshold sign-in lockout (v1.6.6 Item 17): after [MAX_ATTEMPTS] consecutive failed
 * sign-ins, block the button for [COOLDOWN_SECONDS]. Mirrors the PIN lockout
 * (`PinLockoutPolicy`) in shape — deliberately flat, not progressive.
 *
 * This is **client-side deterrence + parity with the PIN lock**, not real brute-force protection:
 * a real attacker hits GoTrue directly and never touches this app. It only closes the "login
 * screen with no attempt cap" gap; API-level rate limiting stays Supabase's job (Item 17 decision,
 * 2026-07-14). Held in memory in the ViewModel — no persistence, so a process restart clears it.
 */
object LoginLockoutPolicy {
    const val MAX_ATTEMPTS = 5
    const val COOLDOWN_SECONDS = 30L

    /** Outcome of counting one more failed sign-in. */
    data class Result(val failedAttempts: Int, val lockoutSeconds: Long)

    /**
     * Call once a sign-in has actually failed on wrong credentials (never on a network error, and
     * never while already locked out). When the threshold is reached the counter resets to 0 and a
     * cooldown starts, so lockouts recur in clean [MAX_ATTEMPTS]-strike cycles.
     */
    fun onFailure(currentFailedAttempts: Int): Result {
        val attempts = currentFailedAttempts + 1
        return if (attempts >= MAX_ATTEMPTS) {
            Result(failedAttempts = 0, lockoutSeconds = COOLDOWN_SECONDS)
        } else {
            Result(failedAttempts = attempts, lockoutSeconds = 0L)
        }
    }
}
