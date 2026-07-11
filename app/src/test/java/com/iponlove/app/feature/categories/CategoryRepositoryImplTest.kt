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

    // ---- manual reorder (item 9b) -----------------------------------------------------

    @Test
    fun reorderCategories_writesIndexAsPosition_andMarksOnlyChangedRowsDirty() = runTest {
        dao.store["a"] = categoryEntity(id = "a", position = 0, updatedAt = Instant.ofEpochMilli(1_000))
        dao.store["b"] = categoryEntity(id = "b", position = 1, updatedAt = Instant.ofEpochMilli(1_000))
        dao.store["c"] = categoryEntity(id = "c", position = 2, updatedAt = Instant.ofEpochMilli(1_000))

        repository.reorderCategories(listOf("c", "a", "b"))

        assertThat(dao.store.getValue("c").position).isEqualTo(0)
        assertThat(dao.store.getValue("a").position).isEqualTo(1)
        assertThat(dao.store.getValue("b").position).isEqualTo(2)
        assertThat(dao.store.getValue("a").pendingSync).isTrue()
        assertThat(dao.store.getValue("a").updatedAt).isEqualTo(now)
        assertThat(dao.store.getValue("b").pendingSync).isTrue()
        assertThat(dao.store.getValue("c").pendingSync).isTrue()
    }

    @Test
    fun reorderCategories_skipsRowsWhosePositionIsUnchanged() = runTest {
        dao.store["a"] = categoryEntity(id = "a", position = 0, updatedAt = Instant.ofEpochMilli(1_000), pendingSync = false)
        dao.store["b"] = categoryEntity(id = "b", position = 1, updatedAt = Instant.ofEpochMilli(1_000), pendingSync = false)

        repository.reorderCategories(listOf("a", "b"))

        assertThat(dao.store.getValue("a").pendingSync).isFalse()
        assertThat(dao.store.getValue("a").updatedAt).isEqualTo(Instant.ofEpochMilli(1_000))
        assertThat(dao.store.getValue("b").pendingSync).isFalse()
    }

    @Test
    fun reorderCategories_ignoresUnknownIds() = runTest {
        dao.store["a"] = categoryEntity(id = "a", position = 0)

        repository.reorderCategories(listOf("ghost", "a"))

        assertThat(dao.store.getValue("a").position).isEqualTo(1)
        assertThat(dao.store.keys).containsExactly("a")
    }

    // ---- shared categories (ADR-0018) -----------------------------------------------

    @Test
    fun shareCategory_makesCoupleOwned_keepingCreator() = runTest {
        dao.store["c"] = categoryEntity(id = "c", userId = "user-1", createdBy = "user-1")

        repository.shareCategory("c", coupleId = "couple-1")

        val row = dao.store.getValue("c")
        assertThat(row.userId).isNull()
        assertThat(row.coupleId).isEqualTo("couple-1")
        assertThat(row.createdBy).isEqualTo("user-1")
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun unshareCategory_byCreator_revertsToCreator() = runTest {
        // I (user-1) created it → I may make it personal again.
        dao.store["c"] = categoryEntity(
            id = "c", userId = null, coupleId = "couple-1", createdBy = "user-1",
        )

        repository.unshareCategory("c")

        val row = dao.store.getValue("c")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.coupleId).isNull()
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun unshareCategory_byNonCreator_isNoOp() = runTest {
        // Creator-only (ADR-0018, v1.6.5 Item 20): the other member (owner-2) created it, so
        // user-1 un-sharing it must not stamp owner-2's user_id onto an un-pushable row.
        val original = categoryEntity(
            id = "c", userId = null, coupleId = "couple-1", createdBy = "owner-2",
        )
        dao.store["c"] = original

        repository.unshareCategory("c")

        assertThat(dao.store.getValue("c")).isEqualTo(original)
    }

    @Test
    fun purgePartnerData_revertsMine_deletesPartnersCoupleRows_andReplicas() = runTest {
        dao.store["mine"] = categoryEntity(
            id = "mine", userId = null, coupleId = "couple-1", createdBy = "user-1",
        )
        dao.store["theirs"] = categoryEntity(
            id = "theirs", userId = null, coupleId = "couple-1", createdBy = "owner-2",
        )
        dao.store["replica"] = categoryEntity(id = "replica", userId = "owner-2", coupleId = null)
        dao.store["personal"] = categoryEntity(id = "personal", userId = "user-1", coupleId = null)

        repository.purgePartnerData()

        assertThat(dao.store.keys).containsExactly("mine", "personal")
        assertThat(dao.store.getValue("mine").userId).isEqualTo("user-1")
        assertThat(dao.store.getValue("mine").coupleId).isNull()
    }
}
