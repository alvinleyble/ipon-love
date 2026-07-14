package com.iponlove.app.feature.auth.domain.usecase

import com.iponlove.app.feature.auth.domain.model.AuthError
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.user.domain.usecase.GetAccountEmailUseCase
import javax.inject.Inject

/**
 * Change the signed-in user's password from Settings (v1.6.5 Item 8). Unlike the recovery-flow
 * [UpdatePasswordUseCase] — where the recovery session already proves identity — a logged-in
 * change re-authenticates with the current password first, so an unlocked phone can't silently
 * reset it. Validation runs before any network round-trip; the re-auth throws INVALID_CREDENTIALS
 * on a wrong current password, leaving the password unchanged.
 */
class ChangePasswordUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val getAccountEmail: GetAccountEmailUseCase,
) {
    suspend operator fun invoke(currentPassword: String, newPassword: String, confirmPassword: String) {
        AuthCredentials.validatePassword(newPassword)
        AuthCredentials.validatePasswordsMatch(newPassword, confirmPassword)
        if (newPassword == currentPassword) throw AuthException(AuthError.SAME_AS_OLD_PASSWORD)
        val email = getAccountEmail() ?: throw AuthException(AuthError.UNKNOWN)
        // Re-auth verifies the current password (and refreshes the session) before the change.
        repository.signIn(email, currentPassword)
        repository.updatePassword(newPassword)
    }
}
