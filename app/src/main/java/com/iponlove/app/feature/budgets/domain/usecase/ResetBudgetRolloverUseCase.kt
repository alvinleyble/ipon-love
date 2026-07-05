package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

/**
 * "Reset rollover" — severs an accumulated carry-forward (surplus or deficit alike) going
 * into next month, without any new persisted marker (ADR-0036 follow-up). Turning rollover
 * off for one month is sufficient on its own: [BudgetProgressCalculator.effectiveLimit]
 * never recurses past a row whose own [Budget.rolloverEnabled] is false, so nothing before
 * that point is reachable by any later month — even one that re-enables rollover afterward.
 *
 * If next month already has a budget for this category, only its rollover flag is cleared;
 * its own amount is left untouched. Otherwise a new one is created, defaulting to this
 * month's amount as a starting point (editable afterward like any other budget).
 */
class ResetBudgetRolloverUseCase @Inject constructor(
    private val repository: BudgetRepository,
) {
    suspend operator fun invoke(budget: Budget, sameCategoryBudgets: List<Budget>) {
        require(budget.rolloverEnabled) { "Rollover is not enabled on this budget" }
        val nextMonth = YearMonth.parse(budget.yearMonth).plusMonths(1).toString()
        val next = sameCategoryBudgets.firstOrNull { it.yearMonth == nextMonth }
        val target = next?.copy(rolloverEnabled = false)
            ?: Budget(
                id = UUID.randomUUID().toString(),
                categoryId = budget.categoryId,
                amount = budget.amount,
                yearMonth = nextMonth,
                rolloverEnabled = false,
            )
        repository.upsertBudget(target)
    }
}
