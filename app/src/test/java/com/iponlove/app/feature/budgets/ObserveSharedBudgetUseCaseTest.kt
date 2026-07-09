package com.iponlove.app.feature.budgets

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import com.iponlove.app.feature.budgets.domain.usecase.ObserveSharedBudgetUseCase
import com.iponlove.app.core.entitlement.Entitlement
import com.iponlove.app.feature.user.domain.model.User
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class ObserveSharedBudgetUseCaseTest {

    private val currentUser = MutableStateFlow<User?>(null)
    private val sharedByCouple = mutableMapOf<String, List<Budget>>()
    private val useCase = ObserveSharedBudgetUseCase(FakeUserRepo(currentUser), FakeBudgetRepo(sharedByCouple))

    @Test
    fun emitsEmpty_whenNotPaired() = runTest {
        currentUser.value = user(coupleId = null)

        useCase().test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun emitsCouplesSharedBudgets_whenPaired() = runTest {
        sharedByCouple["c-1"] = listOf(budget("s", categoryId = null, amount = "30000.00"))
        currentUser.value = user(coupleId = "c-1")

        useCase().test {
            assertThat(awaitItem().map { it.id }).containsExactly("s")
        }
    }

    @Test
    fun switchesToEmpty_onUnpair() = runTest {
        sharedByCouple["c-1"] = listOf(budget("s", categoryId = null))
        currentUser.value = user(coupleId = "c-1")

        useCase().test {
            assertThat(awaitItem().map { it.id }).containsExactly("s")
            currentUser.value = user(coupleId = null) // unpair signal (ADR-0008)
            assertThat(awaitItem()).isEmpty()
        }
    }

    private fun user(coupleId: String?) =
        User(id = "user-1", displayName = "Pat", accentColor = null, coupleId = coupleId)

    private class FakeUserRepo(private val current: MutableStateFlow<User?>) : UserRepository {
        override fun observeCurrentUser(): Flow<User?> = current
        override fun observePartner(coupleId: String): Flow<User?> = emptyFlow()
        override suspend fun ensureLocalRow(userId: String, displayName: String?) = Unit
        override suspend fun updateAccentColor(color: String) = Unit
        override suspend fun updateDisplayName(name: String) = Unit
        override suspend fun getSelfEntitlement(): Entitlement? = null
        override fun observeSelfEntitlement(): Flow<Entitlement> = emptyFlow()
        override fun observePartnerEntitlement(): Flow<Entitlement?> = emptyFlow()
        override suspend fun writeSelfEntitlement(entitlement: Entitlement, checkedAt: Instant) = Unit
    }

    private class FakeBudgetRepo(private val shared: Map<String, List<Budget>>) : BudgetRepository {
        override fun observeBudgets(): Flow<List<Budget>> = emptyFlow()
        override fun observeSharedBudgets(coupleId: String): Flow<List<Budget>> =
            flowOf(shared[coupleId].orEmpty())
        override suspend fun getBudget(id: String): Budget? = null
        override suspend fun upsertBudget(budget: Budget) = Unit
        override suspend fun upsertSharedBudget(budget: Budget, coupleId: String) = Unit
        override suspend fun deleteBudget(id: String) = Unit
        override suspend fun purgeSharedBudgets() = Unit
    }
}
