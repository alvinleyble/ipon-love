package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.PlanLimits
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import javax.inject.Inject

/**
 * The budget count-cap gate (S7 / Item 35). The cap is **per-month** (budgets are inherently
 * monthly), so [yearMonth] is the month the new budget lands in — the displayed month for an add,
 * next month for a duplicate.
 *
 * [shared] selects the tier scope, mirroring [com.iponlove.app.feature.accounts.domain.usecase.CheckAccountCapUseCase]:
 * a **personal** budget is `Scope.INDIVIDUAL` against `maxPersonalBudgets`; a **shared** couple
 * budget is `Scope.SHARED` against `maxSharedBudgets` (either partner's premium unlocks it, D1),
 * gated at the moment a new shared budget is created. Everything stays dormant until enforcement
 * flips (the gate short-circuits to [CapCheck.Allowed] before it reads any limit).
 */
class CheckBudgetCapUseCase @Inject constructor(
    private val repository: BudgetRepository,
    private val gate: PremiumGate,
) {
    suspend operator fun invoke(yearMonth: String, shared: Boolean): CapCheck =
        if (shared) {
            gate.checkCap(Scope.SHARED, repository.countSharedBudgets(yearMonth), PlanLimits::maxSharedBudgets)
        } else {
            gate.checkCap(Scope.INDIVIDUAL, repository.countPersonalBudgets(yearMonth), PlanLimits::maxPersonalBudgets)
        }
}
