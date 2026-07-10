package com.iponlove.app.feature.categories

import com.iponlove.app.feature.categories.data.local.CategoryDao
import com.iponlove.app.feature.categories.data.local.CategoryEntity
import com.iponlove.app.feature.categories.data.remote.CategoryDto
import com.iponlove.app.feature.categories.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** In-memory [CategoryDao] mirroring the real query semantics for fast JVM tests. */
class FakeCategoryDao : CategoryDao {
    val store = linkedMapOf<String, CategoryEntity>()
    private val changes = MutableStateFlow(0)

    override fun observeCategories(userId: String, includeArchived: Boolean): Flow<List<CategoryEntity>> =
        changes.map {
            store.values
                .filter {
                    (it.userId == userId || it.coupleId != null) &&
                        !it.isDeleted && (includeArchived || !it.isArchived)
                }
                .sortedWith(compareBy({ it.position }, { it.createdAt }))
        }

    override fun observeAll(): Flow<List<CategoryEntity>> =
        changes.map { store.values.filter { !it.isDeleted } }

    override suspend fun getById(id: String): CategoryEntity? = store[id]

    override suspend fun countOwned(userId: String): Int =
        store.values.count { it.userId == userId && !it.isDeleted }

    override suspend fun countShared(): Int =
        store.values.count { it.coupleId != null && !it.isDeleted }

    override suspend fun deleteById(id: String) {
        store.remove(id)
        changes.value++
    }

    override suspend fun deleteNotOwnedBy(userId: String) {
        store.values.removeAll { it.userId != null && it.userId != userId }
        changes.value++
    }

    override suspend fun revertOwnCoupleRowsToCreator(userId: String, updatedAt: Long) {
        store.values.toList().forEach { row ->
            if (row.coupleId != null && row.createdBy == userId) {
                store[row.id] = row.copy(
                    userId = row.createdBy,
                    coupleId = null,
                    pendingSync = true,
                    updatedAt = Instant.ofEpochMilli(updatedAt),
                )
            }
        }
        changes.value++
    }

    override suspend fun deleteCoupleRowsNotCreatedBy(userId: String) {
        store.values.removeAll { it.coupleId != null && it.createdBy != userId }
        changes.value++
    }

    override suspend fun upsert(category: CategoryEntity) {
        store[category.id] = category
        changes.value++
    }

    override suspend fun dirtyRows(): List<CategoryEntity> = store.values.filter { it.pendingSync }

    override suspend fun clearPending(ids: List<String>) {
        ids.forEach { id -> store[id]?.let { store[id] = it.copy(pendingSync = false) } }
        changes.value++
    }

    override suspend fun applyPullBatch(categories: List<CategoryEntity>) {
        categories.forEach { store[it.id] = it }
        changes.value++
    }
}

fun categoryEntity(
    id: String,
    name: String = "Groceries",
    userId: String? = "user-1",
    coupleId: String? = null,
    createdBy: String? = null,
    type: CategoryType = CategoryType.EXPENSE,
    position: Int = 0,
    isArchived: Boolean = false,
    createdAt: Instant = Instant.ofEpochMilli(1_000),
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
    pendingSync: Boolean = false,
) = CategoryEntity(
    id = id,
    userId = userId,
    coupleId = coupleId,
    createdBy = createdBy,
    name = name,
    type = type,
    icon = null,
    color = null,
    position = position,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = pendingSync,
)

fun categoryDto(
    id: String,
    name: String = "Groceries",
    userId: String? = "user-1",
    coupleId: String? = null,
    createdBy: String? = null,
    type: CategoryType = CategoryType.EXPENSE,
    serverRev: Long? = null,
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
) = CategoryDto(
    id = id,
    userId = userId,
    coupleId = coupleId,
    createdBy = createdBy,
    name = name,
    type = type,
    icon = null,
    color = null,
    position = 0,
    isArchived = false,
    createdAt = Instant.ofEpochMilli(1_000),
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)
