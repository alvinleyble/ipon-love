package com.iponlove.app.feature.savings.domain.usecase

import com.iponlove.app.feature.savings.domain.model.SavingsGoalProgress
import com.iponlove.app.feature.savings.domain.repository.GoalContributionRepository
import com.iponlove.app.feature.savings.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * The goals list with each goal's DERIVED progress: joins the goals stream to the whole
 * active-contribution stream and folds them through [SavingsGoalCalculator]. Re-emits whenever
 * either a goal or a contribution changes, so `savedAmount`/`reached` are always live.
 */
class ObserveSavingsGoalsUseCase @Inject constructor(
    private val goals: SavingsGoalRepository,
    private val contributions: GoalContributionRepository,
) {
    operator fun invoke(): Flow<List<SavingsGoalProgress>> =
        combine(goals.observeGoals(), contributions.observeAllActive()) { g, c ->
            SavingsGoalCalculator.withProgress(g, c)
        }
}
