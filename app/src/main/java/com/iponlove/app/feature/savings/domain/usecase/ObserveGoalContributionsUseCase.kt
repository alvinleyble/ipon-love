package com.iponlove.app.feature.savings.domain.usecase

import com.iponlove.app.feature.savings.domain.model.GoalContribution
import com.iponlove.app.feature.savings.domain.repository.GoalContributionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveGoalContributionsUseCase @Inject constructor(
    private val repository: GoalContributionRepository,
) {
    operator fun invoke(goalId: String): Flow<List<GoalContribution>> =
        repository.observeByGoal(goalId)
}
