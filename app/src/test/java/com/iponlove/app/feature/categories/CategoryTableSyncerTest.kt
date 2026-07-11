package com.iponlove.app.feature.categories

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.categories.data.remote.CategoryDto
import com.iponlove.app.feature.categories.data.remote.CategoryRemoteSource
import com.iponlove.app.feature.categories.data.remote.PartnerCategoryDto
import com.iponlove.app.feature.categories.data.sync.CategoryTableSyncer
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Proves the categories table is correctly wired into the generic sync engine. */
class CategoryTableSyncerTest {

    private class FakeCategoryRemoteSource : CategoryRemoteSource {
        val pushed = mutableListOf<CategoryDto>()
        val serverRows = mutableListOf<CategoryDto>()

        override suspend fun push(rows: List<CategoryDto>): List<String> {
            pushed += rows
            return rows.map { it.id }
        }

        override suspend fun pull(cursor: Long, limit: Int): List<CategoryDto> =
            serverRows.filter { (it.serverRev ?: 0L) > cursor }
                .sortedBy { it.serverRev }
                .take(limit)

        override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerCategoryDto> = emptyList()
    }

    private val dao = FakeCategoryDao()
    private val remote = FakeCategoryRemoteSource()
    private val cursors = InMemoryCursorStore()
    private val syncer = CategoryTableSyncer(dao, remote, CurrentUserProvider { "user-1" }, cursors, ConflictResolver())

    @Test
    fun usesCategoriesTable() {
        assertThat(syncer.table).isEqualTo(SyncTable.CATEGORIES)
    }

    @Test
    fun push_mapsDirtyRowsToDto_andClearsAckedFlag() = runTest {
        dao.store["a"] = categoryEntity(id = "a", name = "Food", pendingSync = true)
        dao.store["b"] = categoryEntity(id = "b", name = "Clean", pendingSync = false)

        syncer.push()

        assertThat(remote.pushed.map { it.id }).containsExactly("a")
        assertThat(dao.store.getValue("a").pendingSync).isFalse()
    }

    @Test
    fun push_skipsRowsThisSessionCannotOwn(): Unit = runTest {
        dao.currentCoupleId = "couple-1"
        dao.store["own"] = categoryEntity(id = "own", userId = "user-1", pendingSync = true)
        dao.store["ours"] = categoryEntity(
            id = "ours", userId = null, coupleId = "couple-1", createdBy = "user-1", pendingSync = true,
        )
        // Poison: a partner-created shared category reverted onto user-2 (Item 20).
        dao.store["poison"] = categoryEntity(id = "poison", userId = "user-2", coupleId = null, pendingSync = true)

        syncer.push()

        assertThat(remote.pushed.map { it.id }).containsExactly("own", "ours")
        assertThat(dao.store.getValue("poison").pendingSync).isTrue()
    }

    @Test
    fun pull_mapsRemoteRowsToEntities_andAdvancesCursor() = runTest {
        remote.serverRows += categoryDto(id = "a", name = "Salary", serverRev = 12)

        syncer.pull()

        val row = dao.store.getValue("a")
        assertThat(row.name).isEqualTo("Salary")
        assertThat(row.pendingSync).isFalse()
        assertThat(cursors.cursor(SyncTable.CATEGORIES)).isEqualTo(12)
    }
}
