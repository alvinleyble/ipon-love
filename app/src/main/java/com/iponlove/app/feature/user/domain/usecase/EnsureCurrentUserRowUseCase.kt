package com.iponlove.app.feature.user.domain.usecase

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.feature.user.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Creates the local Room users row for the signed-in user if it doesn't exist yet,
 * stamped pending_sync = true. The outbox then pushes it on the next sync — before any
 * other table (SyncTable.USERS is ordinal 0) so the FK chain is never violated (ADR-0013).
 *
 * Call once per login, before triggering sync.
 */
class EnsureCurrentUserRowUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val currentUserProvider: CurrentUserProvider,
) {
    suspend operator fun invoke() {
        val userId = currentUserProvider.userId()
        if (userId.isBlank()) return
        // Seed the display name captured at registration (auth metadata) onto the new row;
        // null for older accounts or a sync-timing gap, handled by UI fallbacks (ADR-0016).
        userRepository.ensureLocalRow(userId, currentUserProvider.displayName())
    }
}
