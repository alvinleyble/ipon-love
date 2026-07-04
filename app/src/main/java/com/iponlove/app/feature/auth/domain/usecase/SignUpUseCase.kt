package com.iponlove.app.feature.auth.domain.usecase

import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.auth.domain.repository.SignUpResult
import javax.inject.Inject

/** Validate input, then register. Throws [com.iponlove.app.feature.auth.domain.model.AuthException]. */
class SignUpUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        name: String,
        email: String,
        password: String,
        confirmPassword: String,
    ): SignUpResult {
        AuthCredentials.validateName(name)
        AuthCredentials.validateEmail(email)
        AuthCredentials.validatePassword(password)
        AuthCredentials.validatePasswordsMatch(password, confirmPassword)
        return repository.signUp(name.trim(), email.trim(), password)
    }
}
