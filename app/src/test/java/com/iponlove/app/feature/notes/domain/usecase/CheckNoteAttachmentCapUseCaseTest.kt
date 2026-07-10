package com.iponlove.app.feature.notes.domain.usecase

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
 * The note-attachments media cap gate (S8) — an INDIVIDUAL per-note cap (free = 0, premium = 3).
 * The free = 0 case is the notable edge: unlike every other cap, the *first* attachment is blocked
 * when enforced (there is no free allowance at all), so this test pins that boundary explicitly.
 */
class CheckNoteAttachmentCapUseCaseTest {

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
        CheckNoteAttachmentCapUseCase(PremiumGate(FakeEntitlement(self), FakeAppConfig(enforcement)))

    @Test
    fun `dormant allows the first attachment despite the zero free cap`() = runTest {
        assertThat(useCase(enforcement = false)(currentCount = 0)).isEqualTo(CapCheck.Allowed)
    }

    @Test
    fun `free tier blocks the very first attachment`() = runTest {
        assertThat(useCase(free, enforcement = true)(currentCount = 0))
            .isEqualTo(CapCheck.Blocked(freeLimit = 0, premiumMax = 3))
    }

    @Test
    fun `premium allows attachments`() = runTest {
        assertThat(useCase(premium, enforcement = true)(currentCount = 0)).isEqualTo(CapCheck.Allowed)
    }
}
