package com.iponlove.app.feature.savings.domain.usecase

import com.iponlove.app.feature.savings.domain.repository.GoalContributionRepository
import com.iponlove.app.feature.savings.domain.repository.SavingsGoalRepository
import javax.inject.Inject

/**
 * Creator-only soft delete with cascade (ADR-0025): soft-delete the goal, then soft-delete the
 * caller's own contributions to it. The partner's contribution rows aren't touched here (RLS
 * forbids it) — they fall out of view via the goal-deleted redaction in
 * `partner_goal_contributions` and get purged on the partner's device.
 */
class DeleteSavingsGoalUseCase @Inject constructor(
    private val goals: SavingsGoalRepository,
    private val contributions: GoalContributionRepository,
) {
    suspend operator fun invoke(goalId: String) {
        goals.deleteGoal(goalId)
        contributions.softDeleteOwnForGoal(goalId)
    }
}
