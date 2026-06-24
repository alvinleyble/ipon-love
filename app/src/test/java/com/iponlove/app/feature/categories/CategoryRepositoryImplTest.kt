package com.iponlove.app.feature.categories

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.categories.data.CategoryRepositoryImpl
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class CategoryRepositoryImplTest {

    private val dao = FakeCategoryDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val repository = CategoryRepositoryImpl(dao, clock, currentUser)

    private fun newCategory(id: String, name: String = "Food", type: CategoryType = CategoryType.EXPENSE) =
        Category(id = id, name = name, type = type)

    @Test
    fun upsert_newCategory_stampsOwnerAndSyncColumns() = runTest {
        repository.upsertCategory(newCategory("c"))

        val row = dao.store.getValue("c")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.pendingSync).isTrue()
        assertThat(row.isDeleted).isFalse()
        assertThat(row.serverRev).isNull()
        assertThat(row.updatedAt).isEqualTo(now)
        assertThat(row.createdAt).isEqualTo(now)
    }

    @Test
    fun upsert_existingCategory_advancesUpdatedAtMonotonically_andPreservesProvenance() = runTest {
        dao.store["c"] = categoryEntity(
            id = "c",
            userId = "owner-x",
            createdAt = Instant.ofEpochMilli(1_000),
            updatedAt = Instant.ofEpochMilli(10_000),
            serverRev = 55,
        )
        now = Instant.ofEpochMilli(10_000)

        repository.upsertCategory(newCategory("c", name = "Dining", type = CategoryType.INCOME))

        val row = dao.store.getValue("c")
        assertThat(row.name).isEqualTo("Dining")
        assertThat(row.type).isEqualTo(CategoryType.INCOME)
        assertThat(row.pendingSync).isTrue()
        assertThat(row.updatedAt).isEqualTo(Instant.ofEpochMilli(10_001))
        assertThat(row.userId).isEqualTo("owner-x")
        assertThat(row.createdAt).isEqualTo(Instant.ofEpochMilli(1_000))
        assertThat(row.serverRev).isEqualTo(55)
    }

    @Test
    fun delete_isSoft_setsTombstoneAndMarksDirty() = runTest {
        dao.store["c"] = categoryEntity(id = "c", serverRev = 3)

        repository.deleteCategory("c")

        val row = dao.store.getValue("c")
        assertThat(row.isDeleted).isTrue()
        assertThat(row.pendingSync).isTrue()
        assertThat(row.serverRev).isEqualTo(3)
    }

    @Test
    fun observeCategories_hidesDeleted_andMapsToDomain() = runTest {
        dao.store["a"] = categoryEntity(id = "a", name = "Bills", position = 0)
        dao.store["b"] = categoryEntity(id = "b", name = "Gone", position = 1, isDeleted = true)

        val categories = repository.observeCategories().first()

        assertThat(categories.map { it.name }).containsExactly("Bills")
    }
}
