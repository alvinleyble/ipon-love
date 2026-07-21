package com.iponlove.app.feature.user.domain.usecase

import com.iponlove.app.feature.user.domain.repository.UserRepository
import javax.inject.Inject

/** Persist the current user's chosen motif-avatar key (v1.6.7 Item 3 Leg 1, ADR-0014). */
class UpdateAvatarMotifUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(motif: String) = userRepository.updateAvatarMotif(motif)
}
