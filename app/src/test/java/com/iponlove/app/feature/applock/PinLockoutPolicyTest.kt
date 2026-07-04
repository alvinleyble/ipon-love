package com.iponlove.app.feature.applock

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.applock.domain.model.AppLockPreferences
import com.iponlove.app.feature.applock.domain.model.PinLockoutPolicy
import org.junit.Test
import java.time.Instant

class PinLockoutPolicyTest {

    private val now = Instant.parse("2026-07-04T00:00:00Z")

    @Test
    fun onFailure_belowThreshold_incrementsWithoutLockout() {
        val prefs = AppLockPreferences(failedPinAttempts = 2)
        val next = PinLockoutPolicy.onFailure(prefs, now)
        assertThat(next.failedPinAttempts).isEqualTo(3)
        assertThat(next.pinLockoutUntil).isNull()
    }

    @Test
    fun onFailure_reachingThreshold_setsLockoutUntilNowPlusCooldown() {
        val prefs = AppLockPreferences(failedPinAttempts = 4)
        val next = PinLockoutPolicy.onFailure(prefs, now)
        assertThat(next.failedPinAttempts).isEqualTo(5)
        assertThat(next.pinLockoutUntil).isEqualTo(now.plusSeconds(PinLockoutPolicy.COOLDOWN_SECONDS))
    }

    @Test
    fun onFailure_pastThreshold_reArmsLockoutEveryTime() {
        // Counter never resets on elapsed time — a failure right after the cooldown expires
        // immediately re-locks, since attempts already sit at/above MAX_ATTEMPTS.
        val prefs = AppLockPreferences(failedPinAttempts = 5, pinLockoutUntil = null)
        val next = PinLockoutPolicy.onFailure(prefs, now)
        assertThat(next.failedPinAttempts).isEqualTo(6)
        assertThat(next.pinLockoutUntil).isEqualTo(now.plusSeconds(PinLockoutPolicy.COOLDOWN_SECONDS))
    }

    @Test
    fun onSuccess_resetsAttemptsAndLockout() {
        val prefs = AppLockPreferences(failedPinAttempts = 5, pinLockoutUntil = now.plusSeconds(30))
        val next = PinLockoutPolicy.onSuccess(prefs)
        assertThat(next.failedPinAttempts).isEqualTo(0)
        assertThat(next.pinLockoutUntil).isNull()
    }

    @Test
    fun isLockedOut_beforeLockoutExpiry_isTrue() {
        val prefs = AppLockPreferences(pinLockoutUntil = now.plusSeconds(10))
        assertThat(PinLockoutPolicy.isLockedOut(prefs, now)).isTrue()
    }

    @Test
    fun isLockedOut_afterLockoutExpiry_isFalse() {
        val prefs = AppLockPreferences(pinLockoutUntil = now.minusSeconds(1))
        assertThat(PinLockoutPolicy.isLockedOut(prefs, now)).isFalse()
    }

    @Test
    fun isLockedOut_noLockoutSet_isFalse() {
        assertThat(PinLockoutPolicy.isLockedOut(AppLockPreferences(), now)).isFalse()
    }

    @Test
    fun remainingLockoutSeconds_roundsDownToZeroFloor() {
        val prefs = AppLockPreferences(pinLockoutUntil = now.minusSeconds(100))
        assertThat(PinLockoutPolicy.remainingLockoutSeconds(prefs, now)).isEqualTo(0)
    }

    @Test
    fun remainingLockoutSeconds_midCooldown_returnsExactRemainder() {
        val prefs = AppLockPreferences(pinLockoutUntil = now.plusSeconds(12))
        assertThat(PinLockoutPolicy.remainingLockoutSeconds(prefs, now)).isEqualTo(12)
    }
}
