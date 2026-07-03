package com.iponlove.app.feature.savings.domain.usecase

import com.iponlove.app.feature.savings.domain.repository.GoalContributionRepository
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class AddGoalContributionUseCase @Inject constructor(
    private val repository: GoalContributionRepository,
) {
    suspend operator fun invoke(
        goalId: String,
        amount: BigDecimal,
        date: Instant,
        note: String?,
    ) = repository.addContribution(goalId, amount, date, note)
}
