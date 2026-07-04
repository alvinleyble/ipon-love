package com.iponlove.app.feature.auth.domain.usecase

import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

/** Validate input, then trigger Supabase's password-recovery email. */
class SendPasswordResetUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(email: String) {
        AuthCredentials.validateEmail(email)
        repository.sendPasswordReset(email.trim())
    }
}
