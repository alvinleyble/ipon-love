package com.iponlove.app.feature.accounts.domain.usecase

import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.PlanLimits
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import javax.inject.Inject

/**
 * The account count-cap gate (S7). Owns the G1 row count (data access stays in the domain layer,
 * per the scalability rule) and defers the tier/enforcement decision to [PremiumGate]. Called at
 * create-intent (personal) and at the share-transition ([shared]) — the two moments a new
 * personal/shared account comes into existence.
 */
class CheckAccountCapUseCase @Inject constructor(
    private val repository: AccountRepository,
    private val gate: PremiumGate,
) {
    suspend operator fun invoke(shared: Boolean): CapCheck =
        if (shared) {
            gate.checkCap(Scope.SHARED, repository.countSharedAccounts(), PlanLimits::maxSharedAccounts)
        } else {
            gate.checkCap(Scope.INDIVIDUAL, repository.countOwnedAccounts(), PlanLimits::maxPersonalAccounts)
        }
}
