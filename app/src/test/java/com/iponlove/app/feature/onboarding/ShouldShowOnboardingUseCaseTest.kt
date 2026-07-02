package com.iponlove.app.feature.onboarding

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.onboarding.domain.repository.OnboardingRepository
import com.iponlove.app.feature.onboarding.domain.usecase.ShouldShowOnboardingUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The new-user gate (ADR-0024): post-successful-sync emptiness, not raw local emptiness. */
class ShouldShowOnboardingUseCaseTest {

    private var ownedCategories = 0
    private var ownedAccounts = 0
    private var onboardingDone = false

    private val categoryRepository = object : CategoryRepository {
        override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> =
            throw UnsupportedOperationException()
        override fun observeAllCategories(): Flow<List<Category>> = throw UnsupportedOperationException()
        override suspend fun getCategory(id: String): Category? = null
        override suspend fun countOwnedCategories(): Int = ownedCategories
        override suspend fun upsertCategory(category: Category) = Unit
        override suspend fun setArchived(id: String, archived: Boolean) = Unit
        override suspend fun deleteCategory(id: String) = Unit
        override suspend fun shareCategory(id: String, coupleId: String) = Unit
        override suspend fun unshareCategory(id: String) = Unit
        override suspend fun purgePartnerData() = Unit
    }

    private val accountRepository = object : AccountRepository {
        override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> =
            throw UnsupportedOperationException()
        override suspend fun getAccount(id: String): Account? = null
        override suspend fun countOwnedAccounts(): Int = ownedAccounts
        override suspend fun upsertAccount(account: Account) = Unit
        override suspend fun setArchived(id: String, archived: Boolean) = Unit
        override suspend fun deleteAccount(id: String) = Unit
        override suspend fun shareAccount(id: String, coupleId: String) = Unit
        override suspend fun unshareAccount(id: String) = Unit
        override suspend fun purgePartnerData() = Unit
    }

    private val onboardingRepository = object : OnboardingRepository {
        override suspend fun isOnboardingDone(): Boolean = onboardingDone
        override suspend fun setOnboardingDone() { onboardingDone = true }
        override fun observePairingCardDismissed(): Flow<Boolean> = flowOf(false)
        override suspend fun dismissPairingCard() = Unit
    }

    private val useCase =
        ShouldShowOnboardingUseCase(categoryRepository, accountRepository, onboardingRepository)

    @Test
    fun failedSync_neverShowsOnboarding_evenWhenEmpty() = runTest {
        assertThat(useCase(syncSucceeded = false)).isFalse()
    }

    @Test
    fun onboardingAlreadyDone_neverShowsAgain_evenWhenEmpty() = runTest {
        onboardingDone = true

        assertThat(useCase(syncSucceeded = true)).isFalse()
    }

    @Test
    fun successfulSync_bothEmpty_showsOnboarding() = runTest {
        assertThat(useCase(syncSucceeded = true)).isTrue()
    }

    @Test
    fun successfulSync_ownedCategoriesExist_doesNotShow() = runTest {
        ownedCategories = 1

        assertThat(useCase(syncSucceeded = true)).isFalse()
    }

    @Test
    fun successfulSync_ownedAccountsExist_doesNotShow() = runTest {
        ownedAccounts = 1

        assertThat(useCase(syncSucceeded = true)).isFalse()
    }
}
