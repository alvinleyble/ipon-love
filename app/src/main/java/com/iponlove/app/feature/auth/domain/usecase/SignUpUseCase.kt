package com.iponlove.app.feature.auth.domain.usecase

import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.auth.domain.repository.SignUpResult
import javax.inject.Inject

/** Validate input, then register. Throws [com.iponlove.app.feature.auth.domain.model.AuthException]. */
class SignUpUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): SignUpResult {
        AuthCredentials.validateEmail(email)
        AuthCredentials.validatePassword(password)
        return repository.signUp(email.trim(), password)
    }
}
