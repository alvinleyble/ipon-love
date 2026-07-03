package com.iponlove.app.feature.savings.domain.usecase

import com.iponlove.app.feature.savings.domain.model.GoalContribution
import com.iponlove.app.feature.savings.domain.model.SavingsGoal
import com.iponlove.app.feature.savings.domain.model.SavingsGoalProgress
import java.math.BigDecimal

/**
 * Derives a goal's `savedAmount`/`reached`/`progress` from the append-only contribution
 * ledger (ADR-0025) — the counter is NEVER stored, so two partners contributing concurrently
 * can't clobber each other (distinct row ids; the sum just includes both). Reuses the
 * derived-balance pattern of [com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator].
 *
 * Pass only non-deleted contributions (the repository already filters tombstones). Contributions
 * whose `goalId` is absent from [goals] are ignored — an orphan (e.g. a leftover own-contribution
 * to an ex-partner's goal after unpair) never leaks into a visible sum.
 */
object SavingsGoalCalculator {

    /** Σ of the non-deleted contributions for [goalId]. */
    fun savedAmount(goalId: String, contributions: List<GoalContribution>): BigDecimal =
        contributions.asSequence()
            .filter { it.goalId == goalId }
            .fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }

    /** Each goal with its derived progress, in the goals list's order. */
    fun withProgress(
        goals: List<SavingsGoal>,
        contributions: List<GoalContribution>,
    ): List<SavingsGoalProgress> {
        val sums = HashMap<String, BigDecimal>()
        for (c in contributions) {
            sums[c.goalId] = (sums[c.goalId] ?: BigDecimal.ZERO) + c.amount
        }
        return goals.map { goal ->
            val saved = sums[goal.id] ?: BigDecimal.ZERO
            SavingsGoalProgress(
                goal = goal,
                savedAmount = saved,
                reached = saved >= goal.targetAmount,
                progress = progressOf(saved, goal.targetAmount),
            )
        }
    }

    /** Clamped 0f..1f. A non-positive target is defensive-only (the editor requires > 0). */
    private fun progressOf(saved: BigDecimal, target: BigDecimal): Float {
        if (target <= BigDecimal.ZERO) return if (saved > BigDecimal.ZERO) 1f else 0f
        return saved.divide(target, 4, java.math.RoundingMode.HALF_UP)
            .toFloat()
            .coerceIn(0f, 1f)
    }
}
