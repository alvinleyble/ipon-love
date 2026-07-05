package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

/**
 * Copies [budget]'s amount and rollover setting into next month's budget for the same
 * category, so the user doesn't have to retype it by hand each month (there is no
 * auto-recurring template — budgets are still created manually, this just removes the
 * retyping step for whoever wants the same setup next month).
 *
 * If next month already has a budget for this category, it's updated in place (matching
 * [BudgetsViewModel.save]'s "reuse the existing budget for this category+month" convention) —
 * this is a deliberate, user-initiated action, so overwriting it to match is the expected
 * result, not a silent background write.
 */
class DuplicateBudgetToNextMonthUseCase @Inject constructor(
    private val repository: BudgetRepository,
) {
    suspend operator fun invoke(budget: Budget, sameCategoryBudgets: List<Budget>) {
        val nextMonth = YearMonth.parse(budget.yearMonth).plusMonths(1).toString()
        val existing = sameCategoryBudgets.firstOrNull { it.yearMonth == nextMonth }
        val target = Budget(
            id = existing?.id ?: UUID.randomUUID().toString(),
            categoryId = budget.categoryId,
            amount = budget.amount,
            yearMonth = nextMonth,
            rolloverEnabled = budget.rolloverEnabled,
        )
        repository.upsertBudget(target)
    }
}
