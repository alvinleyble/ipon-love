package com.iponlove.app.feature.onboarding.domain.usecase

import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.onboarding.domain.repository.OnboardingRepository
import javax.inject.Inject

/**
 * The new-user gate (ADR-0024): owned categories *and* accounts both empty, evaluated only
 * after a successful first sync — never raw local emptiness, which would duplicate-seed a
 * reinstalling or second-device user whose real rows haven't pulled down yet.
 *
 * [syncSucceeded] is supplied by the caller (the launch-time sync attempt); a failed or
 * offline first sync always defers — the decision simply retries on the next launch.
 */
class ShouldShowOnboardingUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val onboardingRepository: OnboardingRepository,
) {
    suspend operator fun invoke(syncSucceeded: Boolean): Boolean {
        if (!syncSucceeded) return false
        if (onboardingRepository.isOnboardingDone()) return false
        return categoryRepository.countOwnedCategories() == 0 &&
            accountRepository.countOwnedAccounts() == 0
    }
}
