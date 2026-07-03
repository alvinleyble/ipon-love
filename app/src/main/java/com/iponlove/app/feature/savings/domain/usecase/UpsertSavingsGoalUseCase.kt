package com.iponlove.app.feature.savings.domain.usecase

import com.iponlove.app.feature.savings.domain.model.SavingsGoal
import com.iponlove.app.feature.savings.domain.repository.SavingsGoalRepository
import javax.inject.Inject

class UpsertSavingsGoalUseCase @Inject constructor(
    private val repository: SavingsGoalRepository,
) {
    suspend operator fun invoke(goal: SavingsGoal) = repository.upsertGoal(goal)
}
