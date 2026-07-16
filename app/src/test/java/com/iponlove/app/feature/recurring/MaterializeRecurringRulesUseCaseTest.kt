package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.util.DeterministicUuid
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.recurring.data.RecurringRuleRepositoryImpl
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.MaterializeRecurringRulesUseCase
import com.iponlove.app.feature.transactions.FakeTransactionDao
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.transactionEntity
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
        ruleDao.store["r"] = ruleEntity("r", frequency = RecurringFrequency.MONTHLY, nextDate = jun(1), autoPost = true)

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
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun(1), autoPost = true)

        materialize(asOf = jun(1))

        val id = DeterministicUuid.v5("r:2026-06-01").toString()
        assertThat(txnDao.store.getValue(id).type).isEqualTo(TransactionType.INCOME)
    }

    @Test
    fun isIdempotent_acrossRepeatedPasses() = runTest {
        ruleDao.store["r"] = ruleEntity("r", frequency = RecurringFrequency.WEEKLY, nextDate = jun(1), autoPost = true)

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
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun(1), autoPost = true)

        materialize(asOf = jun(1))

        assertThat(txnDao.store.getValue(id).isDeleted).isTrue() // not resurrected
        assertThat(txnDao.store).hasSize(1)
        // Cursor still advances so the rule keeps moving forward.
        assertThat(ruleDao.store.getValue("r").nextDate).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    @Test
    fun skipsRule_whenTemplateCategoryMissing() = runTest {
        categories = emptyList() // category was deleted; type can't be resolved
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun(1), autoPost = true)

        materialize(asOf = jun(1))

        assertThat(txnDao.store).isEmpty()
        // Cursor untouched — retried next pass once the category reappears.
        assertThat(ruleDao.store.getValue("r").nextDate).isEqualTo(jun(1))
    }

    @Test
    fun confirmOnArrivalRule_isNotMaterialized_andCursorParks() = runTest {
        // autoPost = false (the default) — a due confirm rule must NOT auto-post; it's surfaced
        // as a "To confirm" prompt instead (Item 37). Nothing is written and the cursor parks.
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun(1), autoPost = false)

        materialize(asOf = jun(15))

        assertThat(txnDao.store).isEmpty()
        assertThat(ruleDao.store.getValue("r").nextDate).isEqualTo(jun(1))
    }

    @Test
    fun notDue_createsNothing_andLeavesCursor() = runTest {
        ruleDao.store["r"] = ruleEntity("r", nextDate = LocalDate.of(2026, 7, 1), autoPost = true)

        materialize(asOf = jun(15))

        assertThat(txnDao.store).isEmpty()
        assertThat(ruleDao.store.getValue("r").nextDate).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    private fun jun(day: Int) = LocalDate.of(2026, 6, day)
}
