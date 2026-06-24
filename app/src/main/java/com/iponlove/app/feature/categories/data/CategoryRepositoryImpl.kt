package com.iponlove.app.feature.categories.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.categories.data.local.CategoryDao
import com.iponlove.app.feature.categories.data.local.CategoryEntity
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
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
) : CategoryRepository {

    override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> =
        dao.observeCategories(includeArchived).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getCategory(id: String): Category? = dao.getById(id)?.toDomain()

    override suspend fun upsertCategory(category: Category) {
        val existing = dao.getById(category.id)
        val updatedAt = clock.stamp(existing?.updatedAt)
        dao.upsert(
            CategoryEntity(
                id = category.id,
                userId = existing?.userId ?: currentUser.userId(),
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
    }
}
