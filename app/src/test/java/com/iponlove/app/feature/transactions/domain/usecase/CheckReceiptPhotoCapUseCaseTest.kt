package com.iponlove.app.feature.transactions.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.config.AppConfig
import com.iponlove.app.core.config.AppConfigRepository
import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.Entitlement
import com.iponlove.app.core.entitlement.EntitlementRepository
import com.iponlove.app.core.entitlement.EntitlementSource
import com.iponlove.app.core.entitlement.PremiumGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The receipt-photos media cap gate (S8) — an INDIVIDUAL per-transaction cap (free = 1, premium = 3).
 * The gate's tier/enforcement logic is proven in `PremiumGateTest`; these cases lock the wiring:
 * the right cap field, INDIVIDUAL scope, and the free-vs-premium boundary a receipt draft sees.
 */
class CheckReceiptPhotoCapUseCaseTest {

    private class FakeEntitlement(private val self: Entitlement) : EntitlementRepository {
        override fun observeSelf(): Flow<Entitlement> = flowOf(self)
        override fun observePartner(): Flow<Entitlement?> = flowOf(null)
        override suspend fun reconcile() = Unit
    }

    private class FakeAppConfig(private val enforcement: Boolean) : AppConfigRepository {
        override fun observe(): Flow<AppConfig> =
            flowOf(AppConfig(enforcementEnabled = enforcement, capOverridesJson = null))
        override suspend fun refresh() = Unit
    }

    private val free = Entitlement.NONE
    private val premium = Entitlement(isPremium = true, premiumUntil = null, source = EntitlementSource.PLAY)

    private fun useCase(self: Entitlement = free, enforcement: Boolean) =
        CheckReceiptPhotoCapUseCase(PremiumGate(FakeEntitlement(self), FakeAppConfig(enforcement)))

    @Test
    fun `dormant never blocks`() = runTest {
        assertThat(useCase(enforcement = false)(currentCount = 3)).isEqualTo(CapCheck.Allowed)
    }

    @Test
    fun `free tier allows the first photo`() = runTest {
        assertThat(useCase(free, enforcement = true)(currentCount = 0)).isEqualTo(CapCheck.Allowed)
    }

    @Test
    fun `free tier blocks the second photo`() = runTest {
        assertThat(useCase(free, enforcement = true)(currentCount = 1))
            .isEqualTo(CapCheck.Blocked(freeLimit = 1, premiumMax = 3))
    }

    @Test
    fun `premium allows up to its own ceiling`() = runTest {
        assertThat(useCase(premium, enforcement = true)(currentCount = 2)).isEqualTo(CapCheck.Allowed)
    }
}
