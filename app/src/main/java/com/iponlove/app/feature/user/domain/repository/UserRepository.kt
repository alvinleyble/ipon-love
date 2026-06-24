package com.iponlove.app.feature.user.domain.repository

import com.iponlove.app.feature.user.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    /** Observe the current signed-in user's own row, or null before it is created. */
    fun observeCurrentUser(): Flow<User?>

    /**
     * Observe the partner's replicated row within [coupleId] (the other member), or null
     * until it has synced in. Excludes the current user.
     */
    fun observePartner(coupleId: String): Flow<User?>

    /**
     * Create the local users row for [userId] if it does not already exist, stamped
     * pending_sync so the outbox pushes it on next sync (ADR-0013).
     */
    suspend fun ensureLocalRow(userId: String)
}
