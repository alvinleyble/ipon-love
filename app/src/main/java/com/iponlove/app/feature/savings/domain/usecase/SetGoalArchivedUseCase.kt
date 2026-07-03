package com.iponlove.app.feature.savings.domain.usecase

import com.iponlove.app.feature.savings.domain.repository.SavingsGoalRepository
import javax.inject.Inject

class SetGoalArchivedUseCase @Inject constructor(
    private val repository: SavingsGoalRepository,
) {
    suspend operator fun invoke(goalId: String, archived: Boolean) =
        repository.setArchived(goalId, archived)
}
