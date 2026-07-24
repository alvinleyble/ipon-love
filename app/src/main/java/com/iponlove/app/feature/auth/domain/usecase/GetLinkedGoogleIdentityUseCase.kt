package com.iponlove.app.feature.auth.domain.usecase

import com.iponlove.app.feature.auth.domain.model.LinkedIdentity
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Reads the Google identity currently linked to the signed-in account (ADR-0051), or null if none.
 * Drives the Profile "Connect Google account" row's Connected/Not-connected state.
 */
class GetLinkedGoogleIdentityUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): LinkedIdentity? = repository.linkedGoogleIdentity()
}
