package com.iponlove.app.feature.couple.domain.usecase

import com.iponlove.app.feature.couple.domain.repository.CoupleRepository
import javax.inject.Inject

/** Dissolve the current couple (shared budgets die, shared notes revert to owner). [ADR-0008] */
class UnpairUseCase @Inject constructor(
    private val repository: CoupleRepository,
) {
    suspend operator fun invoke() = repository.unpair()
}
