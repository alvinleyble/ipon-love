package com.iponlove.app.core.entitlement

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class EffectiveAccessTest {

    private val now = Instant.ofEpochMilli(10_000)
    private val active = Entitlement(isPremium = true, premiumUntil = null, source = EntitlementSource.PLAY)
    private val inactive = Entitlement.NONE

    @Test
    fun individualScope_usesSelfOnly() {
        assertThat(EffectiveAccess.hasAccess(Scope.INDIVIDUAL, active, partner = inactive, now)).isTrue()
        assertThat(EffectiveAccess.hasAccess(Scope.INDIVIDUAL, inactive, partner = active, now)).isFalse()
    }

    @Test
    fun sharedScope_eitherPartnerUnlocks() {
        assertThat(EffectiveAccess.hasAccess(Scope.SHARED, active, partner = inactive, now)).isTrue()
        assertThat(EffectiveAccess.hasAccess(Scope.SHARED, inactive, partner = active, now)).isTrue()
        assertThat(EffectiveAccess.hasAccess(Scope.SHARED, inactive, partner = inactive, now)).isFalse()
    }

    @Test
    fun sharedScope_nullPartner_fallsBackToSelfOnly() {
        // Unpaired or partner row not yet synced.
        assertThat(EffectiveAccess.hasAccess(Scope.SHARED, active, partner = null, now)).isTrue()
        assertThat(EffectiveAccess.hasAccess(Scope.SHARED, inactive, partner = null, now)).isFalse()
    }

    @Test
    fun expiredPremiumUntil_isNotActive() {
        val expired = Entitlement(isPremium = true, premiumUntil = now.minusSeconds(1), source = EntitlementSource.PLAY)
        assertThat(EffectiveAccess.hasAccess(Scope.INDIVIDUAL, expired, partner = null, now)).isFalse()
    }

    @Test
    fun futurePremiumUntil_isStillActive() {
        val stillValid = Entitlement(isPremium = true, premiumUntil = now.plusSeconds(1), source = EntitlementSource.PLAY)
        assertThat(EffectiveAccess.hasAccess(Scope.INDIVIDUAL, stillValid, partner = null, now)).isTrue()
    }

    @Test
    fun nullPremiumUntil_neverExpires() {
        // The one-time-purchase shape (D7) — no expiry at all.
        assertThat(EffectiveAccess.hasAccess(Scope.INDIVIDUAL, active, partner = null, now)).isTrue()
    }

    @Test
    fun shouldLock_enforcementOff_neverLocks() {
        assertThat(
            EffectiveAccess.shouldLock(enforcementEnabled = false, Scope.INDIVIDUAL, inactive, partner = inactive, now)
        ).isFalse()
    }

    @Test
    fun shouldLock_enforcementOn_locksWithoutAccess() {
        assertThat(
            EffectiveAccess.shouldLock(enforcementEnabled = true, Scope.INDIVIDUAL, inactive, partner = inactive, now)
        ).isTrue()
    }

    @Test
    fun shouldLock_enforcementOn_doesNotLockWithAccess() {
        assertThat(
            EffectiveAccess.shouldLock(enforcementEnabled = true, Scope.INDIVIDUAL, active, partner = inactive, now)
        ).isFalse()
    }
}
