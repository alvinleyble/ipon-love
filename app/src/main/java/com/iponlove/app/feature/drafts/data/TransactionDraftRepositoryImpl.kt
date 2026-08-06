package com.iponlove.app.feature.drafts.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.session.userIdOrNull
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.feature.drafts.data.local.TransactionDraftDao
import com.iponlove.app.feature.drafts.data.local.TransactionDraftEntity
import com.iponlove.app.feature.drafts.domain.model.TransactionDraft
import com.iponlove.app.feature.drafts.domain.repository.TransactionDraftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [TransactionDraftRepository]. The single place every draft write applies the sync
 * bookkeeping: a fresh monotonic `updated_at` (ADR-0001) and `pending_sync` (ADR-0002); deletes
 * are soft (ADR-0010).
 */
class TransactionDraftRepositoryImpl @Inject constructor(
    private val dao: TransactionDraftDao,
    private val clock: SyncClock,
    private val currentUser: CurrentUserProvider,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
) : TransactionDraftRepository {

    // userId resolved inside the flow, not eagerly: these are re-collected during the sign-out
    // transition (auth already null) where an eager userId() would crash the process.
    override fun observeDrafts(): Flow<List<TransactionDraft>> = flow {
        val userId = currentUser.userIdOrNull()
        if (userId == null) emit(emptyList())
        else emitAll(dao.observeDrafts(userId).map { rows -> rows.map { it.toDomain() } })
    }

    override fun observeDraftCount(): Flow<Int> = flow {
        val userId = currentUser.userIdOrNull()
        if (userId == null) emit(0) else emitAll(dao.observeDraftCount(userId))
    }

    override suspend fun getDraft(id: String): TransactionDraft? =
        dao.getById(id)?.takeIf { !it.isDeleted }?.toDomain()

    override suspend fun saveDraft(draft: TransactionDraft) {
        val existing = dao.getById(draft.id)
        dao.upsert(
            TransactionDraftEntity(
                id = draft.id,
                // Ownership survives a re-park, exactly as it does for a transaction edit.
                userId = existing?.userId ?: currentUser.userId(),
                type = draft.type,
                amount = draft.amount,
                categoryId = draft.categoryId,
                accountId = draft.accountId,
                toAccountId = draft.toAccountId,
                note = draft.note,
                date = draft.date,
                isPrivate = draft.isPrivate,
                receiptCount = draft.receiptCount,
                localImageIds = draft.localImageIds,
                // "Parked 12 days ago" means when it was FIRST parked; re-parking an old draft
                // must not reset its age, or the anti-graveyard label loses its whole point.
                createdAt = existing?.createdAt ?: draft.parkedAt,
                updatedAt = clock.stamp(existing?.updatedAt),
                // A re-parked draft that was previously retired/deleted comes back as active —
                // the user is holding it again.
                isDeleted = false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun deleteDraft(id: String): List<String> {
        val existing = dao.getById(id) ?: return emptyList()
        softDelete(existing)
        return existing.localImageIds
    }

    override suspend fun retireDraft(id: String) {
        // Idempotent by construction: an already-retired (or never-existing) draft is a no-op, so
        // re-promoting the same draft can never double anything (ADR-0066 decision 5). The local
        // image ids are cleared with it — the transaction_images rows own those files now, and
        // leaving them here would keep the sweep's known-id set growing forever.
        val existing = dao.getById(id)?.takeIf { !it.isDeleted } ?: return
        softDelete(existing)
    }

    override suspend fun allLocalImageIds(): List<String> =
        dao.activeDrafts().flatMap { it.localImageIds }

    private suspend fun softDelete(existing: TransactionDraftEntity) {
        dao.upsert(
            existing.copy(
                isDeleted = true,
                localImageIds = emptyList(),
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }
}
