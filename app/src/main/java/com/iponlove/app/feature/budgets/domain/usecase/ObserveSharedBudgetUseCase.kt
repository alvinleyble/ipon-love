package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * The couple's shared budgets, driven off the current user's own `couple_id` (always
 * readable even after RLS cuts off partner reads, ADR-0008): emits an empty list when not
 * paired, otherwise the joint budgets for that couple. Mirrors [ObserveCoupleMembersUseCase]
 * so the combined view can re-derive everything from the single pairing signal.
 */
class ObserveSharedBudgetUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val budgetRepository: BudgetRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<Budget>> =
        userRepository.observeCurrentUser().flatMapLatest { me ->
            val coupleId = me?.coupleId
            if (coupleId == null) flowOf(emptyList())
            else budgetRepository.observeSharedBudgets(coupleId)
        }
}
