package com.iponlove.app.core.entitlement

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.config.AppConfig
import com.iponlove.app.core.config.AppConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/**
 * The create-time count-cap decision (paywall S7). The load-bearing cases: the dormant fast path
 * (enforcement OFF never blocks), the free-tier block at/over the cap, the premium bypass, the
 * SHARED either-partner-unlocks rule (D1), and a tier-scoped `cap_overrides` reshaping the numbers.
 */
class PremiumGateTest {

    private class FakeEntitlement(
        private val self: Entitlement,
        private val partner: Entitlement? = null,
    ) : EntitlementRepository {
        override fun observeSelf(): Flow<Entitlement> = flowOf(self)
        override fun observePartner(): Flow<Entitlement?> = flowOf(partner)
        override suspend fun reconcile() = Unit
    }

    private class FakeAppConfig(private val config: AppConfig) : AppConfigRepository {
        override fun observe(): Flow<AppConfig> = flowOf(config)
        override suspend fun refresh() = Unit
    }

    private val free = Entitlement.NONE
    private val premium = Entitlement(isPremium = true, premiumUntil = null, source = EntitlementSource.PLAY)

    private fun gate(
        self: Entitlement = free,
        partner: Entitlement? = null,
        enforcement: Boolean,
        overrides: String? = null,
    ) = PremiumGate(
        entitlement = FakeEntitlement(self, partner),
        appConfig = FakeAppConfig(AppConfig(enforcementEnabled = enforcement, capOverridesJson = overrides)),
    )

    @Test
    fun `dormant enforcement never blocks even over the cap`() = runTest {
        val result = gate(self = free, enforcement = false)
            .checkCap(Scope.INDIVIDUAL, currentCount = 999, limitOf = PlanLimits::maxPersonalAccounts)
        assertThat(result).isEqualTo(CapCheck.Allowed)
    }

    @Test
    fun `free tier at the cap is blocked with both numbers`() = runTest {
        val result = gate(self = free, enforcement = true)
            .checkCap(Scope.INDIVIDUAL, currentCount = 10, limitOf = PlanLimits::maxPersonalAccounts)
        assertThat(result).isEqualTo(CapCheck.Blocked(freeLimit = 10, premiumMax = 100))
    }

    @Test
    fun `free tier under the cap is allowed`() = runTest {
        val result = gate(self = free, enforcement = true)
            .checkCap(Scope.INDIVIDUAL, currentCount = 9, limitOf = PlanLimits::maxPersonalAccounts)
        assertThat(result).isEqualTo(CapCheck.Allowed)
    }

    @Test
    fun `premium self bypasses the cap`() = runTest {
        val result = gate(self = premium, enforcement = true)
            .checkCap(Scope.INDIVIDUAL, currentCount = 50, limitOf = PlanLimits::maxPersonalAccounts)
        assertThat(result).isEqualTo(CapCheck.Allowed)
    }

    @Test
    fun `shared scope is unlocked by the partner's premium (D1)`() = runTest {
        val result = gate(self = free, partner = premium, enforcement = true)
            .checkCap(Scope.SHARED, currentCount = 5, limitOf = PlanLimits::maxSharedAccounts)
        assertThat(result).isEqualTo(CapCheck.Allowed)
    }

    @Test
    fun `shared scope with both free and over cap is blocked`() = runTest {
        val result = gate(self = free, partner = free, enforcement = true)
            .checkCap(Scope.SHARED, currentCount = 1, limitOf = PlanLimits::maxSharedAccounts)
        assertThat(result).isEqualTo(CapCheck.Blocked(freeLimit = 1, premiumMax = 50))
    }

    @Test
    fun `individual scope ignores a premium partner`() = runTest {
        val result = gate(self = free, partner = premium, enforcement = true)
            .checkCap(Scope.INDIVIDUAL, currentCount = 10, limitOf = PlanLimits::maxPersonalAccounts)
        assertThat(result).isEqualTo(CapCheck.Blocked(freeLimit = 10, premiumMax = 100))
    }

    @Test
    fun `tier-scoped cap_overrides reshape both numbers`() = runTest {
        val overrides = """{"free":{"maxPersonalAccounts":2},"premium":{"maxPersonalAccounts":20}}"""
        val result = gate(self = free, enforcement = true, overrides = overrides)
            .checkCap(Scope.INDIVIDUAL, currentCount = 2, limitOf = PlanLimits::maxPersonalAccounts)
        assertThat(result).isEqualTo(CapCheck.Blocked(freeLimit = 2, premiumMax = 20))
    }

    @Test
    fun `expired premium falls back to the free cap`() = runTest {
        val expired = Entitlement(
            isPremium = true,
            premiumUntil = Instant.now().minusSeconds(60),
            source = EntitlementSource.PLAY,
        )
        val result = gate(self = expired, enforcement = true)
            .checkCap(Scope.INDIVIDUAL, currentCount = 10, limitOf = PlanLimits::maxPersonalAccounts)
        assertThat(result).isEqualTo(CapCheck.Blocked(freeLimit = 10, premiumMax = 100))
    }

    // --- observeLocked: the S9 boolean soft-gate seam ---

    @Test
    fun `observeLocked is false while dormant even for a free user`() = runTest {
        val locked = gate(self = free, enforcement = false).observeLocked().first()
        assertThat(locked).isFalse()
    }

    @Test
    fun `observeLocked is true for an enforced free user`() = runTest {
        val locked = gate(self = free, enforcement = true).observeLocked().first()
        assertThat(locked).isTrue()
    }

    @Test
    fun `observeLocked is false for an enforced premium user`() = runTest {
        val locked = gate(self = premium, enforcement = true).observeLocked().first()
        assertThat(locked).isFalse()
    }

    @Test
    fun `observeLocked (individual, the S9 default) ignores a premium partner`() = runTest {
        // All S9 soft gates are individual — a partner's premium must not unlock the user's own
        // palette/calculator/rollover/calendar.
        val locked = gate(self = free, partner = premium, enforcement = true).observeLocked().first()
        assertThat(locked).isTrue()
    }

    @Test
    fun `observeLocked shared scope is unlocked by a premium partner (D1)`() = runTest {
        val locked = gate(self = free, partner = premium, enforcement = true)
            .observeLocked(Scope.SHARED).first()
        assertThat(locked).isFalse()
    }
}
