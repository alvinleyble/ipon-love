package com.iponlove.app.feature.categories.domain.usecase

import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.PlanLimits
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * The category count-cap gate (S7) — mirror of [com.iponlove.app.feature.accounts.domain.usecase
 * .CheckAccountCapUseCase]. Owns the G1 row count and defers the tier/enforcement decision to
 * [PremiumGate]; called at create-intent (personal) and at the share-transition ([shared]).
 */
class CheckCategoryCapUseCase @Inject constructor(
    private val repository: CategoryRepository,
    private val gate: PremiumGate,
) {
    suspend operator fun invoke(shared: Boolean): CapCheck =
        if (shared) {
            gate.checkCap(Scope.SHARED, repository.countSharedCategories(), PlanLimits::maxSharedCategories)
        } else {
            gate.checkCap(Scope.INDIVIDUAL, repository.countOwnedCategories(), PlanLimits::maxPersonalCategories)
        }
}
