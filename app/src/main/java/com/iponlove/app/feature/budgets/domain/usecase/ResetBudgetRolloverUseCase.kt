package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import javax.inject.Inject

/**
 * "Reset rollover" — resets this month's carried-in balance (surplus or deficit alike)
 * without any new persisted marker (ADR-0036 follow-up / ADR-0041). Clearing
 * [Budget.rolloverEnabled] on the passed-in month M is sufficient on its own:
 * [BudgetProgressCalculator.effectiveLimit] never recurses past a row whose own
 * [Budget.rolloverEnabled] is false, so M immediately shows its own [Budget.amount] and
 * nothing before M is reachable by any later month — even one that re-enables rollover.
 *
 * Acts on M itself (the month the user is looking at), not M+1: the reset is visible on
 * the target month. M's rollover toggle reads OFF afterward; the chain restarts at M, so
 * M+1 inherits only M's own `amount − spent`.
 */
class ResetBudgetRolloverUseCase @Inject constructor(
    private val repository: BudgetRepository,
) {
    suspend operator fun invoke(budget: Budget) {
        require(budget.rolloverEnabled) { "Rollover is not enabled on this budget" }
        repository.upsertBudget(budget.copy(rolloverEnabled = false))
    }
}
