package com.iponlove.app.feature.drafts.domain.repository

import com.iponlove.app.feature.drafts.domain.model.TransactionDraft
import kotlinx.coroutines.flow.Flow

/**
 * Parked-draft source of truth (Room-backed, ADR-0066). Writes funnel through here so the sync
 * bookkeeping — monotonic `updated_at` (ADR-0001), `pending_sync` (ADR-0002), soft delete only
 * (ADR-0010) — is applied in one place.
 */
interface TransactionDraftRepository {

    /** The user's own active drafts, oldest first. Empty while signed out. */
    fun observeDrafts(): Flow<List<TransactionDraft>>

    /** Count of the same set — what the pinned "Drafts (N)" card on Records reads. */
    fun observeDraftCount(): Flow<Int>

    suspend fun getDraft(id: String): TransactionDraft?

    /** Create or update a parked draft. Preserves [TransactionDraft.parkedAt] across re-parks. */
    suspend fun saveDraft(draft: TransactionDraft)

    /**
     * The user's explicit delete — a soft delete (ADR-0010), the only thing that ever retires a
     * draft besides promotion. There is **no auto-expiry, ever**: the user's mental model is that
     * the app is holding the draft *for* them (ADR-0066 decision 10).
     *
     * Returns the local image ids the draft was holding, so the caller can release their files —
     * without that, the v1.7.0 Item 14 storage leak returns through a new door.
     */
    suspend fun deleteDraft(id: String): List<String>

    /**
     * Retires the draft whose id matches a transaction that has **just been written**
     * (ADR-0066 decision 5). Files are deliberately *not* released — the `transaction_images`
     * rows own them from here on. A no-op when no such draft exists, which is what makes
     * re-promotion idempotent.
     */
    suspend fun retireDraft(id: String)

    /**
     * Every local image id held by an **active** draft — the set
     * [com.iponlove.app.feature.transactions.domain.usecase.CleanupOrphanedReceiptsUseCase]
     * unions into its known ids so a parked draft's photo survives the sweep (decision 6).
     */
    suspend fun allLocalImageIds(): List<String>
}
