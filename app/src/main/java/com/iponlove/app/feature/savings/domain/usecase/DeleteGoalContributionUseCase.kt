package com.iponlove.app.feature.savings.domain.usecase

import com.iponlove.app.feature.savings.domain.repository.GoalContributionRepository
import javax.inject.Inject

class DeleteGoalContributionUseCase @Inject constructor(
    private val repository: GoalContributionRepository,
) {
    suspend operator fun invoke(id: String) = repository.deleteContribution(id)
}
