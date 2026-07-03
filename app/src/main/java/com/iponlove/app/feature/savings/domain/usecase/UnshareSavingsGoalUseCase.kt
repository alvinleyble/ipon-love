package com.iponlove.app.feature.savings.domain.usecase

import com.iponlove.app.feature.savings.domain.repository.SavingsGoalRepository
import javax.inject.Inject

class UnshareSavingsGoalUseCase @Inject constructor(
    private val repository: SavingsGoalRepository,
) {
    suspend operator fun invoke(goalId: String) = repository.unshareGoal(goalId)
}
