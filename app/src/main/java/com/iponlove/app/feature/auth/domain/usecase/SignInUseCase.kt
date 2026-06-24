package com.iponlove.app.feature.auth.domain.usecase

import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

/** Validate input, then sign in. Throws [com.iponlove.app.feature.auth.domain.model.AuthException]. */
class SignInUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String) {
        AuthCredentials.validateEmail(email)
        repository.signIn(email.trim(), password)
    }
}
