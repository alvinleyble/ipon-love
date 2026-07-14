package com.iponlove.app.feature.auth.domain.usecase

import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Best-effort refresh of the signed-in user from the server (Item 8). Pulls a server-confirmed
 * change — e.g. a completed email change — into the local session so the UI reflects it without a
 * restart. Swallows failures (offline is expected); the caller keeps showing its cached value.
 */
class RefreshSessionUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke() {
        runCatching { repository.refreshCurrentUser() }
    }
}
