package com.iponlove.app.feature.categories.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.session.userIdOrNull
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.feature.categories.data.local.CategoryDao
import com.iponlove.app.feature.categories.data.local.CategoryEntity
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [CategoryRepository]. The single place every category write applies the
 * sync bookkeeping: a fresh monotonic `updated_at` (ADR-0001) and `pending_sync`
 * (ADR-0002); deletes are soft (ADR-0010).
 */
class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
    private val clock: SyncClock,
    private val currentUser: CurrentUserProvider,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
) : CategoryRepository {

    // userId resolved inside the flow, not eagerly: re-collected during the sign-out
    // transition (auth already null) where an eager userId() would crash the process.
    override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> = flow {
        val userId = currentUser.userIdOrNull()
        if (userId == null) emit(emptyList())
        else emitAll(dao.observeCategories(userId, includeArchived).map { rows -> rows.map { it.toDomain() } })
    }

    override fun observeAllCategories(): Flow<List<Category>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getCategory(id: String): Category? = dao.getById(id)?.toDomain()

    override suspend fun countOwnedCategories(): Int = dao.countOwned(currentUser.userId())

    override suspend fun upsertCategory(category: Category) {
        val existing = dao.getById(category.id)
        val updatedAt = clock.stamp(existing?.updatedAt)
        val me = currentUser.userId()
        dao.upsert(
            CategoryEntity(
                id = category.id,
                // Ownership is managed by share/unshare, never the editor, so it survives edits.
                userId = if (existing != null) existing.userId else me,
                coupleId = existing?.coupleId,
                createdBy = existing?.createdBy ?: me,
                name = category.name,
                type = category.type,
                icon = category.icon,
                color = category.color,
                position = category.position,
                isArchived = category.isArchived,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun reorderCategories(orderedIds: List<String>) {
        var changed = false
        orderedIds.forEachIndexed { index, id ->
            val existing = dao.getById(id) ?: return@forEachIndexed
            if (existing.position == index) return@forEachIndexed
            dao.upsert(
                existing.copy(
                    position = index,
                    updatedAt = clock.stamp(existing.updatedAt),
                    pendingSync = true,
                ),
            )
            changed = true
        }
        if (changed) syncTrigger.requestPush()
    }

    override suspend fun shareCategory(id: String, coupleId: String) {
        val existing = dao.getById(id) ?: return
        if (existing.coupleId != null) return // already shared
        dao.upsert(
            existing.copy(
                userId = null,
                coupleId = coupleId,
                createdBy = existing.createdBy ?: existing.userId,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun unshareCategory(id: String) {
        val existing = dao.getById(id) ?: return
        val creator = existing.createdBy ?: existing.userId ?: return
        dao.upsert(
            existing.copy(
                userId = creator,
                coupleId = null,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                isArchived = archived,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun deleteCategory(id: String) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun purgePartnerData() {
        val me = currentUser.userId()
        dao.revertOwnCoupleRowsToCreator(me, clock.stamp(null).toEpochMilli())
        dao.deleteCoupleRowsNotCreatedBy(me)
        dao.deleteNotOwnedBy(me)
    }
}
