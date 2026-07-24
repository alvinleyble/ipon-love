package com.iponlove.app.feature.auth.domain.usecase

import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Links a Google identity to the signed-in account (ADR-0051) from a Google ID token already
 * obtained on-device. Keeps in-app Google linking on the same UseCase→Repository spine as
 * sign-in. Throws [com.iponlove.app.feature.auth.domain.model.AuthException] on failure.
 */
class LinkGoogleIdentityUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(idToken: String, nonce: String) =
        repository.linkGoogleIdentity(idToken, nonce)
}
