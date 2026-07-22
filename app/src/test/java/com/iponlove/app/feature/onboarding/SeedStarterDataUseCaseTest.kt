package com.iponlove.app.feature.onboarding

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.onboarding.domain.model.StarterBundle
import com.iponlove.app.feature.onboarding.domain.usecase.SeedStarterDataUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SeedStarterDataUseCaseTest {

    /** Global call order across both repos, so FK ordering (ADR-0009) is checkable. */
    private val writeLog = mutableListOf<String>()
    private val savedCategories = mutableListOf<Category>()
    private val savedAccounts = mutableListOf<Account>()

    private val categoryRepository = object : CategoryRepository {
        override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> =
            throw UnsupportedOperationException()
        override fun observeAllCategories(): Flow<List<Category>> = throw UnsupportedOperationException()
        override suspend fun getCategory(id: String): Category? = null
        override suspend fun countOwnedCategories(): Int = 0
        override suspend fun countSharedCategories(): Int = 0
        override suspend fun upsertCategory(category: Category) {
            writeLog += "category:${category.name}"
            savedCategories += category
        }
        override suspend fun reorderCategories(orderedIds: List<String>) = Unit
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
        override suspend fun countOwnedAccounts(): Int = 0
        override suspend fun countSharedAccounts(): Int = 0
        override suspend fun upsertAccount(account: Account) {
            writeLog += "account:${account.name}"
            savedAccounts += account
        }
        override suspend fun reorderAccounts(orderedIds: List<String>) = Unit
        override suspend fun setArchived(id: String, archived: Boolean) = Unit
        override suspend fun deleteAccount(id: String) = Unit
        override suspend fun shareAccount(id: String, coupleId: String) = Unit
        override suspend fun unshareAccount(id: String) = Unit
        override suspend fun purgePartnerData() = Unit
    }

    private val useCase = SeedStarterDataUseCase(categoryRepository, accountRepository) { "user-1" }

    @Test
    fun seedsOnlySelectedBundles() = runTest {
        useCase(setOf(StarterBundle.INCOME))

        assertThat(savedCategories.map { it.name }).containsExactly("Salary", "Business", "Gifts")
        assertThat(savedAccounts).isEmpty()
    }

    @Test
    fun accountsWrittenBeforeCategories_fkOrder() = runTest {
        useCase(setOf(StarterBundle.ACCOUNTS, StarterBundle.EVERYDAY_SPENDING))

        val lastAccountIndex = writeLog.indexOfLast { it.startsWith("account:") }
        val firstCategoryIndex = writeLog.indexOfFirst { it.startsWith("category:") }
        assertThat(lastAccountIndex).isLessThan(firstCategoryIndex)
    }

    @Test
    fun categoryPositions_runContinuouslyAcrossSelectedBundles() = runTest {
        useCase(setOf(StarterBundle.EVERYDAY_SPENDING, StarterBundle.INCOME))

        assertThat(savedCategories.map { it.position }).isEqualTo((0 until savedCategories.size).toList())
    }

    @Test
    fun sameUser_repeatedInvocation_producesIdenticalIds() = runTest {
        useCase(setOf(StarterBundle.INCOME))
        val firstIds = savedCategories.map { it.id }
        savedCategories.clear()

        useCase(setOf(StarterBundle.INCOME))
        val secondIds = savedCategories.map { it.id }

        assertThat(secondIds).isEqualTo(firstIds)
    }

    @Test
    fun emptyBundleSet_seedsNothing() = runTest {
        useCase(emptySet())

        assertThat(savedCategories).isEmpty()
        assertThat(savedAccounts).isEmpty()
    }

    @Test
    fun reimbursablesBundle_seedsPassThroughPair_bothFlagged() = runTest {
        useCase(setOf(StarterBundle.REIMBURSABLES))

        assertThat(savedCategories.map { it.name }).containsExactly("Reimbursable", "Reimbursement")
        // Both legs (expense + income) carry the exclude flag so the pass-through washes out
        // of Analysis on both sides (ADR-0049).
        assertThat(savedCategories.all { it.excludeFromAnalysis }).isTrue()
    }
}
