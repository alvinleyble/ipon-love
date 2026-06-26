package com.iponlove.app.feature.user.domain.usecase

import com.iponlove.app.feature.auth.domain.usecase.AuthCredentials
import com.iponlove.app.feature.user.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Renames the current user. Reuses [AuthCredentials.validateName] so the in-app edit holds
 * the same trimmed/non-blank/≤50 rule as registration, then persists via the existing
 * UserDto sync path (ADR-0016). Throws on invalid input.
 */
class UpdateDisplayNameUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(name: String) {
        AuthCredentials.validateName(name)
        userRepository.updateDisplayName(name.trim())
    }
}
