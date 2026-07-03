package com.iponlove.app.feature.savings.domain.usecase

import com.iponlove.app.feature.savings.domain.model.SavingsGoal
import com.iponlove.app.feature.savings.domain.repository.SavingsGoalRepository
import javax.inject.Inject

class GetSavingsGoalUseCase @Inject constructor(
    private val repository: SavingsGoalRepository,
) {
    suspend operator fun invoke(id: String): SavingsGoal? = repository.getGoal(id)
}
