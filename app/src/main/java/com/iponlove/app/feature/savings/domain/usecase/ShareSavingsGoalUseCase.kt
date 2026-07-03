package com.iponlove.app.feature.savings.domain.usecase

import com.iponlove.app.feature.savings.domain.repository.SavingsGoalRepository
import javax.inject.Inject

class ShareSavingsGoalUseCase @Inject constructor(
    private val repository: SavingsGoalRepository,
) {
    suspend operator fun invoke(goalId: String, coupleId: String) =
        repository.shareGoal(goalId, coupleId)
}
