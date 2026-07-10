package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.PlanLimits
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import javax.inject.Inject

/**
 * The budget count-cap gate (S7). Budgets are individual-scope only (§10.1 `maxBudgets`) and the
 * cap is **per-month** (budgets are inherently monthly), so [yearMonth] is the month the new
 * budget lands in — the displayed month for an add, next month for a duplicate. Shared couple
 * budgets are a separate concept with their own limits.
 */
class CheckBudgetCapUseCase @Inject constructor(
    private val repository: BudgetRepository,
    private val gate: PremiumGate,
) {
    suspend operator fun invoke(yearMonth: String): CapCheck =
        gate.checkCap(Scope.INDIVIDUAL, repository.countPersonalBudgets(yearMonth), PlanLimits::maxBudgets)
}
