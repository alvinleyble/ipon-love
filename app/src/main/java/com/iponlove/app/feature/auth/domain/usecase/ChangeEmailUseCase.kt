package com.iponlove.app.feature.auth.domain.usecase

import com.iponlove.app.feature.auth.domain.model.AuthError
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.user.domain.usecase.GetAccountEmailUseCase
import javax.inject.Inject

/**
 * Change the signed-in user's email from Settings (v1.6.5 Item 8). Re-authenticates with the
 * current password first (an unlocked phone shouldn't be able to hijack the account address),
 * then requests the change — Supabase emails a confirmation link and the address does not flip
 * until it's clicked. Blocks a no-op change to the same address before the round-trip.
 */
class ChangeEmailUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val getAccountEmail: GetAccountEmailUseCase,
) {
    suspend operator fun invoke(currentPassword: String, newEmail: String) {
        AuthCredentials.validateEmail(newEmail)
        val trimmed = newEmail.trim()
        val currentEmail = getAccountEmail() ?: throw AuthException(AuthError.UNKNOWN)
        if (trimmed.equals(currentEmail, ignoreCase = true)) {
            throw AuthException(AuthError.INVALID_EMAIL)
        }
        // Re-auth verifies the current password (and refreshes the session) before the change.
        repository.signIn(currentEmail, currentPassword)
        repository.updateEmail(trimmed)
    }
}
