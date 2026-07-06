package com.iponlove.app.feature.savings.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.session.userIdOrNull
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.feature.savings.data.local.GoalContributionDao
import com.iponlove.app.feature.savings.data.local.GoalContributionEntity
import com.iponlove.app.feature.savings.domain.model.GoalContribution
import com.iponlove.app.feature.savings.domain.repository.GoalContributionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Room-backed [GoalContributionRepository]. Each [addContribution] mints a **fresh random id**
 * (never deterministic), so two partners contributing concurrently write independent rows that
 * never clobber each other under LWW (ADR-0025). Edits/deletes are gated to your own rows.
 */
class GoalContributionRepositoryImpl @Inject constructor(
    private val dao: GoalContributionDao,
    private val clock: SyncClock,
    private val currentUser: CurrentUserProvider,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
) : GoalContributionRepository {

    // userId resolved inside each flow, not eagerly: re-collected during the sign-out
    // transition (auth already null) where an eager userId() would crash the process.
    override fun observeAllActive(): Flow<List<GoalContribution>> = flow {
        val userId = currentUser.userIdOrNull()
        if (userId == null) emit(emptyList())
        else emitAll(dao.observeAllActive().map { rows -> rows.map { it.toDomain(userId) } })
    }

    override fun observeByGoal(goalId: String): Flow<List<GoalContribution>> = flow {
        val userId = currentUser.userIdOrNull()
        if (userId == null) emit(emptyList())
        else emitAll(dao.observeByGoal(goalId).map { rows -> rows.map { it.toDomain(userId) } })
    }

    override suspend fun addContribution(
        goalId: String,
        amount: BigDecimal,
        date: Instant,
        note: String?,
    ) {
        val now = clock.stamp(null)
        dao.upsert(
            GoalContributionEntity(
                id = UUID.randomUUID().toString(), // random — the crux of LWW-safety (ADR-0025)
                goalId = goalId,
                userId = currentUser.userId(),
                amount = amount,
                note = note?.ifBlank { null },
                date = date,
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                serverRev = null,
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun editContribution(
        id: String,
        amount: BigDecimal,
        date: Instant,
        note: String?,
    ) = mutateOwn(id) { it.copy(amount = amount, date = date, note = note?.ifBlank { null }) }

    override suspend fun deleteContribution(id: String) =
        mutateOwn(id) { it.copy(isDeleted = true) }

    override suspend fun softDeleteOwnForGoal(goalId: String) {
        val mine = dao.activeOwnedForGoal(goalId, currentUser.userId())
        if (mine.isEmpty()) return
        for (row in mine) {
            dao.upsert(
                row.copy(
                    isDeleted = true,
                    updatedAt = clock.stamp(row.updatedAt),
                    pendingSync = true,
                ),
            )
        }
        syncTrigger.requestPush()
    }

    override suspend fun purgePartnerData(userId: String) = dao.deleteNotOwnedBy(userId)

    /** Apply [change] to your own contribution [id] with fresh sync bookkeeping; no-op otherwise. */
    private suspend inline fun mutateOwn(
        id: String,
        change: (GoalContributionEntity) -> GoalContributionEntity,
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
