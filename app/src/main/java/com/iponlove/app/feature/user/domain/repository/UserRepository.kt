package com.iponlove.app.feature.user.domain.repository

import com.iponlove.app.core.entitlement.Entitlement
import com.iponlove.app.feature.user.domain.model.User
import kotlinx.coroutines.flow.Flow
import java.time.Instant

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
     * Persist [motif] (a motif-avatar key, e.g. "leaf") as the current user's avatar and mark the
     * row pending_sync so it pushes on next sync — a synced cosmetic like the accent color
     * (v1.6.7 Item 3 Leg 1, ADR-0014).
     */
    suspend fun updateAvatarMotif(motif: String)

    /**
     * Persist [name] as the current user's display name and mark the row pending_sync so it
     * pushes on next sync via the existing UserDto path (ADR-0016).
     */
    suspend fun updateDisplayName(name: String)

    // --- Premium entitlement (the 4 users-row columns, D2 / ADR-0044). Row mechanics only —
    // the reconcile *policy* (GRANT-skip, idempotency) lives in core/entitlement's
    // EntitlementRepository, which consumes these. ---

    /**
     * The current user's own entitlement, or null before the row exists (a fresh login before
     * [ensureLocalRow], or the sign-out window). The reconcile loop reads this to decide whether
     * to touch the row at all (G7 GRANT-skip + idempotency).
     */
    suspend fun getSelfEntitlement(): Entitlement?

    /** Observe the current user's own entitlement; [Entitlement.NONE] before the row/first sync. */
    fun observeSelfEntitlement(): Flow<Entitlement>

    /**
     * Observe the partner's entitlement within the couple, read straight off the replicated
     * partner row (no separate redacting view — the columns ride the same-couple `users_select`
     * policy, ADR-0044 §1/S1). Null when unpaired or the partner row hasn't synced in yet.
     */
    fun observePartnerEntitlement(): Flow<Entitlement?>

    /**
     * Cache [entitlement] onto the current user's row (dirty + pushed), stamping [checkedAt] as
     * the last-reconcile diagnostic (`entitlement_checked_at`). Offset-corrected monotonic
     * `updated_at` and `pending_sync=true` like every other write; no-op if the row doesn't exist.
     */
    suspend fun writeSelfEntitlement(entitlement: Entitlement, checkedAt: Instant)
}
