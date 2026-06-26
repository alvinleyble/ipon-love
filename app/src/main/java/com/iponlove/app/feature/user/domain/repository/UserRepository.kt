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
     * Create the local users row for [userId] if it does not already exist, seeding
     * [displayName] (from auth metadata — ADR-0016) and stamping pending_sync so the outbox
     * pushes it on next sync (ADR-0013). An existing local or server row is left untouched.
     */
    suspend fun ensureLocalRow(userId: String, displayName: String?)

    /**
     * Persist [color] (hex string, e.g. "#1565C0") as the current user's couple attribution
     * color and mark the row pending_sync so it pushes on next sync.
     */
    suspend fun updateAccentColor(color: String)

    /**
     * Persist [name] as the current user's display name and mark the row pending_sync so it
     * pushes on next sync via the existing UserDto path (ADR-0016).
     */
    suspend fun updateDisplayName(name: String)
}
