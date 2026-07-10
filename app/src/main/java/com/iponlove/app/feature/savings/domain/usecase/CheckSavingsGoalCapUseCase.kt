package com.iponlove.app.feature.savings.domain.usecase

import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.PlanLimits
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.feature.savings.domain.repository.SavingsGoalRepository
import javax.inject.Inject

/**
 * The savings-goal count-cap gate (S7). A new goal is born personal (checked at create/save); the
 * shared cap ([shared]) is checked when a goal is turned couple-owned — the two moments a
 * personal/shared goal comes into existence.
 */
class CheckSavingsGoalCapUseCase @Inject constructor(
    private val repository: SavingsGoalRepository,
    private val gate: PremiumGate,
) {
    suspend operator fun invoke(shared: Boolean): CapCheck =
        if (shared) {
            gate.checkCap(Scope.SHARED, repository.countSharedGoals(), PlanLimits::maxSharedSavingsGoals)
        } else {
            gate.checkCap(Scope.INDIVIDUAL, repository.countPersonalGoals(), PlanLimits::maxPersonalSavingsGoals)
        }
}
