package com.iponlove.app.feature.user.domain.usecase

import com.iponlove.app.feature.user.domain.repository.UserRepository
import javax.inject.Inject

class UpdateAccentColorUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(color: String) = userRepository.updateAccentColor(color)
}
