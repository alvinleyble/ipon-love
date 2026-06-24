package com.iponlove.app.feature.budgets.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.budgets.data.local.BudgetDao
import com.iponlove.app.feature.budgets.data.local.BudgetEntity
import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [BudgetRepository]. The single place every budget write applies the sync
 * bookkeeping: a fresh monotonic `updated_at` (ADR-0001) and `pending_sync` (ADR-0002);
 * deletes are soft (ADR-0010). New budgets are personal (owner set, couple null);
 * ownership survives edits.
 */
class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao,
    private val clock: SyncClock,
    private val currentUser: CurrentUserProvider,
) : BudgetRepository {

    override fun observeBudgets(): Flow<List<Budget>> =
        dao.observeBudgets().map { rows -> rows.map { it.toDomain() } }

    override fun observeSharedBudgets(coupleId: String): Flow<List<Budget>> =
        dao.observeSharedBudgets(coupleId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getBudget(id: String): Budget? = dao.getById(id)?.toDomain()

    override suspend fun upsertBudget(budget: Budget) {
        val existing = dao.getById(budget.id)
        val updatedAt = clock.stamp(existing?.updatedAt)
        dao.upsert(
            BudgetEntity(
                id = budget.id,
                userId = existing?.userId ?: currentUser.userId(),
                coupleId = existing?.coupleId,
                categoryId = budget.categoryId,
                amount = budget.amount,
                yearMonth = budget.yearMonth,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
    }

    override suspend fun upsertSharedBudget(budget: Budget, coupleId: String) {
        val existing = dao.getById(budget.id)
        val updatedAt = clock.stamp(existing?.updatedAt)
        dao.upsert(
            BudgetEntity(
                id = budget.id,
                // Couple-owned: schema's owner check requires user_id null when couple_id set.
                userId = null,
                coupleId = existing?.coupleId ?: coupleId,
                categoryId = budget.categoryId,
                amount = budget.amount,
                yearMonth = budget.yearMonth,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
    }

    override suspend fun deleteBudget(id: String) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
    }

    override suspend fun purgeSharedBudgets() = dao.deleteCoupleBudgets()
}
