package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.util.DeterministicUuid
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.recurring.data.RecurringRuleRepositoryImpl
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.MaterializeRecurringRulesUseCase
import com.iponlove.app.feature.transactions.FakeTransactionDao
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.transactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MaterializeRecurringRulesUseCaseTest {

    private val ruleDao = FakeRecurringRuleDao()
    private val txnDao = FakeTransactionDao()
    private val clock = SyncClock(now = { Instant.ofEpochMilli(1_000) })
    private val currentUser = CurrentUserProvider { "user-1" }

    private val ruleRepository = RecurringRuleRepositoryImpl(ruleDao, clock, currentUser)
    private val txnRepository = TransactionRepositoryImpl(txnDao, clock, currentUser)

    private var categories = listOf(category("cat-1", CategoryType.EXPENSE))
    private val observeCategories = ObserveCategoriesUseCase(FakeCategoryRepository { categories })

    private val materialize =
        MaterializeRecurringRulesUseCase(ruleRepository, txnRepository, observeCategories)

    @Test
    fun materializesDueOccurrences_withDeterministicId_resolvedType_andProvenance() = runTest {
        ruleDao.store["r"] = ruleEntity("r", frequency = RecurringFrequency.MONTHLY, nextDate = jun(1))

        materialize(asOf = jun(1))

        val expectedId = DeterministicUuid.v5("r:2026-06-01").toString()
        val row = txnDao.store.getValue(expectedId)
        assertThat(row.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(row.recurringRuleId).isEqualTo("r")
        assertThat(row.pendingSync).isTrue()
        // Cursor advanced one interval past the generated occurrence.
        assertThat(ruleDao.store.getValue("r").nextDate).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    @Test
    fun incomeCategory_yieldsIncomeTransaction() = runTest {
        categories = listOf(category("cat-1", CategoryType.INCOME))
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun(1))

        materialize(asOf = jun(1))

        val id = DeterministicUuid.v5("r:2026-06-01").toString()
        assertThat(txnDao.store.getValue(id).type).isEqualTo(TransactionType.INCOME)
    }

    @Test
    fun isIdempotent_acrossRepeatedPasses() = runTest {
        ruleDao.store["r"] = ruleEntity("r", frequency = RecurringFrequency.WEEKLY, nextDate = jun(1))

        materialize(asOf = jun(22))
        val afterFirst = txnDao.store.size
        materialize(asOf = jun(22))

        assertThat(afterFirst).isEqualTo(4) // Jun 1, 8, 15, 22
        assertThat(txnDao.store.size).isEqualTo(afterFirst) // second pass adds nothing
    }

    @Test
    fun doesNotResurrectDeletedOccurrence() = runTest {
        // A tombstone already exists for this occurrence's deterministic id (e.g. user deleted it,
        // pulled from another device), and this device's rule cursor still points at that date.
        val id = DeterministicUuid.v5("r:2026-06-01").toString()
        txnDao.store[id] = transactionEntity(id = id, isDeleted = true)
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun(1))

        materialize(asOf = jun(1))

        assertThat(txnDao.store.getValue(id).isDeleted).isTrue() // not resurrected
        assertThat(txnDao.store).hasSize(1)
        // Cursor still advances so the rule keeps moving forward.
        assertThat(ruleDao.store.getValue("r").nextDate).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    @Test
    fun skipsRule_whenTemplateCategoryMissing() = runTest {
        categories = emptyList() // category was deleted; type can't be resolved
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun(1))

        materialize(asOf = jun(1))

        assertThat(txnDao.store).isEmpty()
        // Cursor untouched — retried next pass once the category reappears.
        assertThat(ruleDao.store.getValue("r").nextDate).isEqualTo(jun(1))
    }

    @Test
    fun notDue_createsNothing_andLeavesCursor() = runTest {
        ruleDao.store["r"] = ruleEntity("r", nextDate = LocalDate.of(2026, 7, 1))

        materialize(asOf = jun(15))

        assertThat(txnDao.store).isEmpty()
        assertThat(ruleDao.store.getValue("r").nextDate).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    private fun jun(day: Int) = LocalDate.of(2026, 6, day)

    private fun category(id: String, type: CategoryType) =
        Category(id = id, name = id, type = type)

    private class FakeCategoryRepository(
        private val supply: () -> List<Category>,
    ) : CategoryRepository {
        override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> =
            flowOf(supply())

        override fun observeAllCategories(): Flow<List<Category>> = flowOf(supply())

        override suspend fun getCategory(id: String): Category? = supply().firstOrNull { it.id == id }
        override suspend fun countOwnedCategories(): Int = error("unused")
        override suspend fun upsertCategory(category: Category) = error("unused")
        override suspend fun reorderCategories(orderedIds: List<String>) = error("unused")
        override suspend fun setArchived(id: String, archived: Boolean) = error("unused")
        override suspend fun deleteCategory(id: String) = error("unused")
        override suspend fun shareCategory(id: String, coupleId: String) = error("unused")
        override suspend fun unshareCategory(id: String) = error("unused")
        override suspend fun purgePartnerData() = error("unused")
    }
}
