package com.iponlove.app.feature.couple.domain.usecase

import com.iponlove.app.feature.couple.domain.repository.CoupleRepository
import javax.inject.Inject

/** Create a couple for the current user. Throws PairingException on failure. */
class CreateCoupleUseCase @Inject constructor(
    private val repository: CoupleRepository,
) {
    suspend operator fun invoke(name: String) = repository.createCouple(name.trim())
}
