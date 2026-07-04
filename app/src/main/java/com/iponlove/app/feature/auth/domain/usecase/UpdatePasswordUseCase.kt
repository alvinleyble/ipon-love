package com.iponlove.app.feature.auth.domain.usecase

import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

/** Validate the new password (and its confirmation), then apply it to the recovery session. */
class UpdatePasswordUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(newPassword: String, confirmPassword: String) {
        AuthCredentials.validatePassword(newPassword)
        AuthCredentials.validatePasswordsMatch(newPassword, confirmPassword)
        repository.updatePassword(newPassword)
    }
}
