package com.iponlove.app.feature.savings.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.session.userIdOrNull
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.feature.savings.data.local.SavingsGoalDao
import com.iponlove.app.feature.savings.data.local.SavingsGoalEntity
import com.iponlove.app.feature.savings.domain.model.SavingsGoal
import com.iponlove.app.feature.savings.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [SavingsGoalRepository]. Every write stamps a fresh monotonic `updated_at`
 * (ADR-0001) + `pending_sync` (ADR-0002); deletes are soft (ADR-0010). Metadata is
 * creator-owned, so writes on a replicated partner goal (`userId != me`) are no-ops — the UI
 * hides those controls, and this is the defensive backstop.
 */
class SavingsGoalRepositoryImpl @Inject constructor(
    private val dao: SavingsGoalDao,
    private val clock: SyncClock,
    private val currentUser: CurrentUserProvider,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
) : SavingsGoalRepository {

    // userId resolved inside the flow, not eagerly: re-collected during the sign-out
    // transition (auth already null) where an eager userId() would crash the process.
    override fun observeGoals(): Flow<List<SavingsGoal>> = flow {
        val userId = currentUser.userIdOrNull()
        if (userId == null) emit(emptyList())
        else emitAll(dao.observeGoals(userId).map { rows -> rows.map { it.toDomain(userId) } })
    }

    override suspend fun getGoal(id: String): SavingsGoal? =
        dao.getById(id)?.toDomain(currentUser.userId())

    override suspend fun upsertGoal(goal: SavingsGoal) {
        val existing = dao.getById(goal.id)
        // Editing someone else's shared goal is not allowed (creator owns the metadata).
        if (existing != null && existing.userId != currentUser.userId()) return
        val updatedAt = clock.stamp(existing?.updatedAt)
        dao.upsert(
            SavingsGoalEntity(
                id = goal.id,
                userId = existing?.userId ?: currentUser.userId(),
                coupleId = existing?.coupleId,
                isShared = existing?.isShared ?: false,
                name = goal.name,
                targetAmount = goal.targetAmount,
                targetDate = goal.targetDate,
                icon = goal.icon,
                color = goal.color,
                isArchived = existing?.isArchived ?: false,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun setArchived(id: String, archived: Boolean) =
        mutateOwn(id) { it.copy(isArchived = archived) }

    override suspend fun deleteGoal(id: String) =
        mutateOwn(id) { it.copy(isDeleted = true) }

    override suspend fun shareGoal(id: String, coupleId: String) =
        mutateOwn(id) { it.copy(isShared = true, coupleId = coupleId) }

    // Retain coupleId so the un-share crosses the partner's redacting view (ADR-0005).
    override suspend fun unshareGoal(id: String) =
        mutateOwn(id) { it.copy(isShared = false) }

    override suspend fun purgePartnerData(userId: String) = dao.deleteNotOwnedBy(userId)

    /** Apply [change] to your own goal [id] with fresh sync bookkeeping; no-op on a partner row. */
    private suspend inline fun mutateOwn(
        id: String,
        change: (SavingsGoalEntity) -> SavingsGoalEntity,
    ) {
        val existing = dao.getById(id) ?: return
        if (existing.userId != currentUser.userId()) return
        dao.upsert(
            change(existing).copy(
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }
}
