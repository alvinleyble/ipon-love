package com.iponlove.app.feature.auth

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.auth.domain.model.LoginLockoutPolicy
import org.junit.Test

class LoginLockoutPolicyTest {

    @Test
    fun `an early failure increments the count without locking`() {
        val result = LoginLockoutPolicy.onFailure(currentFailedAttempts = 0)
        assertThat(result.failedAttempts).isEqualTo(1)
        assertThat(result.lockoutSeconds).isEqualTo(0L)
    }

    @Test
    fun `the failure just below threshold still does not lock`() {
        val result = LoginLockoutPolicy.onFailure(currentFailedAttempts = LoginLockoutPolicy.MAX_ATTEMPTS - 2)
        assertThat(result.failedAttempts).isEqualTo(LoginLockoutPolicy.MAX_ATTEMPTS - 1)
        assertThat(result.lockoutSeconds).isEqualTo(0L)
    }

    @Test
    fun `reaching the threshold locks and resets the counter`() {
        val result = LoginLockoutPolicy.onFailure(currentFailedAttempts = LoginLockoutPolicy.MAX_ATTEMPTS - 1)
        assertThat(result.lockoutSeconds).isEqualTo(LoginLockoutPolicy.COOLDOWN_SECONDS)
        // Reset to 0 so lockouts recur in clean MAX_ATTEMPTS-strike cycles.
        assertThat(result.failedAttempts).isEqualTo(0)
    }

    @Test
    fun `walking a full cycle from zero locks exactly once at the threshold`() {
        var attempts = 0
        val lockouts = mutableListOf<Int>()
        repeat(LoginLockoutPolicy.MAX_ATTEMPTS) { i ->
            val result = LoginLockoutPolicy.onFailure(attempts)
            attempts = result.failedAttempts
            if (result.lockoutSeconds > 0) lockouts.add(i)
        }
        // Only the final (MAX_ATTEMPTS-th) strike triggers the lockout.
        assertThat(lockouts).containsExactly(LoginLockoutPolicy.MAX_ATTEMPTS - 1)
        assertThat(attempts).isEqualTo(0)
    }
}
