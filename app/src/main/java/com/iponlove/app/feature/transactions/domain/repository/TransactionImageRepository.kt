package com.iponlove.app.feature.transactions.domain.repository

import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import kotlinx.coroutines.flow.Flow

/**
 * Receipt-images source of truth (Room-backed). Writes funnel through here so the sync
 * bookkeeping — `updated_at` stamping (ADR-0001), `pending_sync` (ADR-0002), soft delete
 * (ADR-0010) — is applied in one place. Mirrors the note-attachment repository.
 */
interface TransactionImageRepository {

    /** Active (non-deleted) images for one transaction, ordered for display. One-shot snapshot
     *  used by the deferred-persistence editor (not a live flow). */
    suspend fun getImages(transactionId: String): List<TransactionImage>

    /**
     * Every active, uploaded receipt image (own + replicated partner), grouped for the combined
     * view: transactionId → ordered image URLs. Pre-upload rows (no URL yet) are excluded.
     */
    fun observeImageUrls(): Flow<Map<String, List<String>>>

    /** Insert a newly-picked image row (localPath set, url null) pending upload. No-op past [TransactionImage.MAX]. */
    suspend fun addImage(transactionId: String, imageId: String, localPath: String)

    /** Remove an image: hard-delete if never uploaded, else soft-delete + push the tombstone. */
    suspend fun deleteImage(id: String)

    /** Hard-delete all replicated partner images on unpair (ADR-0008). */
    suspend fun purgePartnerData(userId: String)

    /** Every row id on record, deleted or not — the "nothing points at this file" cleanup key. */
    suspend fun allImageIds(): List<String>
}
