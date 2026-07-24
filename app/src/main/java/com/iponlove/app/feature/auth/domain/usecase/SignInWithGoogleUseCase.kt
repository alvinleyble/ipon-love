package com.iponlove.app.feature.auth.domain.usecase

import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Signs in with a Google ID token already obtained on-device (ADR-0050). Keeps Google on the same
 * ViewModel→UseCase→Repository spine as email/password. Throws
 * [com.iponlove.app.feature.auth.domain.model.AuthException] on failure.
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(idToken: String, nonce: String) =
        repository.signInWithGoogleIdToken(idToken, nonce)
}
