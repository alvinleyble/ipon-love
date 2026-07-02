package com.iponlove.app.feature.onboarding.domain.usecase

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.util.DeterministicUuid
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.onboarding.domain.model.StarterAccountItem
import com.iponlove.app.feature.onboarding.domain.model.StarterBundle
import com.iponlove.app.feature.onboarding.domain.model.StarterCatalog
import com.iponlove.app.feature.onboarding.domain.model.StarterCategoryItem
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Seeds the onboarding template picker's chosen bundles as personal categories/accounts
 * (ADR-0024). Written in FK order (ADR-0009: accounts before categories, both children of
 * `users`/`couples`).
 *
 * Idempotent by construction: each row's id is [DeterministicUuid.v5] of `userId:key`, so a
 * second call (a re-entered onboarding graph, a retried save) resolves to the same rows and
 * [CategoryRepository.upsertCategory]/[AccountRepository.upsertAccount] overwrite in place
 * rather than duplicating.
 */
class SeedStarterDataUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val currentUser: CurrentUserProvider,
) {
    suspend operator fun invoke(bundles: Set<StarterBundle>) {
        val userId = currentUser.userId()

        if (StarterBundle.ACCOUNTS in bundles) {
            seedAccounts(userId, StarterCatalog.ACCOUNTS)
        }

        // Position is a running counter across every selected category bundle (declared order:
        // Everyday spending, Bills & utilities, Income) so the list reads cleanly regardless of
        // which subset the user kept checked, instead of each bundle restarting at position 0.
        var position = 0
        if (StarterBundle.EVERYDAY_SPENDING in bundles) {
            position = seedCategories(userId, StarterCatalog.EVERYDAY_SPENDING, position)
        }
        if (StarterBundle.BILLS_UTILITIES in bundles) {
            position = seedCategories(userId, StarterCatalog.BILLS_UTILITIES, position)
        }
        if (StarterBundle.INCOME in bundles) {
            seedCategories(userId, StarterCatalog.INCOME, position)
        }
    }

    private suspend fun seedCategories(
        userId: String,
        items: List<StarterCategoryItem>,
        startPosition: Int,
    ): Int {
        items.forEachIndexed { index, item ->
            categoryRepository.upsertCategory(
                Category(
                    id = DeterministicUuid.v5("starter-category:$userId:${item.key}").toString(),
                    name = item.name,
                    type = item.type,
                    icon = item.icon,
                    color = item.color,
                    position = startPosition + index,
                ),
            )
        }
        return startPosition + items.size
    }

    private suspend fun seedAccounts(userId: String, items: List<StarterAccountItem>) {
        items.forEachIndexed { index, item ->
            accountRepository.upsertAccount(
                Account(
                    id = DeterministicUuid.v5("starter-account:$userId:${item.key}").toString(),
                    name = item.name,
                    type = item.type,
                    openingBalance = BigDecimal.ZERO,
                    icon = item.icon,
                    color = item.color,
                    position = index,
                ),
            )
        }
    }
}
