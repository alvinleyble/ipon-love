package com.iponlove.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.config.AppConfig
import com.iponlove.app.core.config.AppConfigRepository
import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.Entitlement
import com.iponlove.app.core.entitlement.EntitlementRepository
import com.iponlove.app.core.entitlement.EntitlementSource
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import com.iponlove.app.feature.budgets.domain.usecase.CheckBudgetCapUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The budget count-cap gate (Item 35). The personal branch uses `maxPersonalBudgets` at
 * INDIVIDUAL scope; the shared branch uses `maxSharedBudgets` at SHARED scope (either partner's
 * premium unlocks it, D1). Everything is dormant until enforcement flips.
 */
class CheckBudgetCapUseCaseTest {

    private class FakeBudgetRepo(
        private val personalCount: Int = 0,
        private val sharedCount: Int = 0,
    ) : BudgetRepository {
        override fun observeBudgets(): Flow<List<Budget>> = emptyFlow()
        override fun observeSharedBudgets(coupleId: String): Flow<List<Budget>> = emptyFlow()
        override suspend fun getBudget(id: String): Budget? = null
        override suspend fun countPersonalBudgets(yearMonth: String): Int = personalCount
        override suspend fun countSharedBudgets(yearMonth: String): Int = sharedCount
        override suspend fun upsertBudget(budget: Budget) = Unit
        override suspend fun upsertSharedBudget(budget: Budget, coupleId: String) = Unit
        override suspend fun deleteBudget(id: String) = Unit
        override suspend fun purgeSharedBudgets() = Unit
    }

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

    private fun useCase(
        personalCount: Int = 0,
        sharedCount: Int = 0,
        self: Entitlement = free,
        partner: Entitlement? = null,
        enforcement: Boolean,
    ) = CheckBudgetCapUseCase(
        repository = FakeBudgetRepo(personalCount, sharedCount),
        gate = PremiumGate(
            entitlement = FakeEntitlement(self, partner),
            appConfig = FakeAppConfig(AppConfig(enforcementEnabled = enforcement, capOverridesJson = null)),
        ),
    )

    @Test
    fun `dormant never blocks either scope even over cap`() = runTest {
        assertThat(useCase(personalCount = 999, enforcement = false).invoke("2026-07", shared = false))
            .isEqualTo(CapCheck.Allowed)
        assertThat(useCase(sharedCount = 999, enforcement = false).invoke("2026-07", shared = true))
            .isEqualTo(CapCheck.Allowed)
    }

    @Test
    fun `enforced free personal at cap is blocked`() = runTest {
        val result = useCase(personalCount = 5, enforcement = true).invoke("2026-07", shared = false)
        assertThat(result).isEqualTo(CapCheck.Blocked(freeLimit = 5, premiumMax = 100))
    }

    @Test
    fun `enforced free shared at cap is blocked`() = runTest {
        // Free shared cap is 1 — the first shared budget is allowed, the second blocked.
        val result = useCase(sharedCount = 1, enforcement = true).invoke("2026-07", shared = true)
        assertThat(result).isEqualTo(CapCheck.Blocked(freeLimit = 1, premiumMax = 50))
    }

    @Test
    fun `enforced free shared under cap is allowed`() = runTest {
        val result = useCase(sharedCount = 0, enforcement = true).invoke("2026-07", shared = true)
        assertThat(result).isEqualTo(CapCheck.Allowed)
    }

    @Test
    fun `premium self bypasses both caps`() = runTest {
        assertThat(useCase(personalCount = 99, self = premium, enforcement = true).invoke("2026-07", shared = false))
            .isEqualTo(CapCheck.Allowed)
        assertThat(useCase(sharedCount = 49, self = premium, enforcement = true).invoke("2026-07", shared = true))
            .isEqualTo(CapCheck.Allowed)
    }

    @Test
    fun `shared cap is unlocked by a premium partner (D1)`() = runTest {
        val result = useCase(sharedCount = 5, self = free, partner = premium, enforcement = true)
            .invoke("2026-07", shared = true)
        assertThat(result).isEqualTo(CapCheck.Allowed)
    }

    @Test
    fun `personal cap ignores a premium partner`() = runTest {
        val result = useCase(personalCount = 5, self = free, partner = premium, enforcement = true)
            .invoke("2026-07", shared = false)
        assertThat(result).isEqualTo(CapCheck.Blocked(freeLimit = 5, premiumMax = 100))
    }
}
