package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.util.DeterministicUuid
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.recurring.data.RecurringRuleRepositoryImpl
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.ObservePendingConfirmationsUseCase
import com.iponlove.app.feature.recurring.domain.usecase.ObserveRecurringRulesUseCase
import com.iponlove.app.feature.transactions.FakeTransactionDao
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.transactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ObservePendingConfirmationsUseCaseTest {

    private val ruleDao = FakeRecurringRuleDao()
    private val txnDao = FakeTransactionDao()
    private val clock = SyncClock(now = { Instant.ofEpochMilli(1_000) })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val ruleRepository = RecurringRuleRepositoryImpl(ruleDao, clock, currentUser)
    private val txnRepository = TransactionRepositoryImpl(txnDao, clock, currentUser)

    private var categories = listOf(category("cat-1", CategoryType.INCOME))
    private val observeCategories = ObserveCategoriesUseCase(FakeCategoryRepository { categories })

    private val today = LocalDate.of(2026, 7, 15)
    private val observePending = ObservePendingConfirmationsUseCase(
        observeRules = ObserveRecurringRulesUseCase(ruleRepository),
        observeCategories = observeCategories,
        transactionRepository = txnRepository,
    )

    /** Invokes with the fixed test `today`. */
    private fun useCase() = observePending(today = { today })

    @Test
    fun confirmRule_surfacesDueOccurrences_oldestFirst_withRuleAmountAsDefault() = runTest {
        // Monthly income rule, cursor parked at Jun 1; today is Jul 15 → Jun 1 and Jul 1 are due.
        ruleDao.store["r"] = ruleEntity(
            "r", frequency = RecurringFrequency.MONTHLY, nextDate = jun(1), amount = "20000.00",
        )

        val pending = useCase().first()

        assertThat(pending.map { it.date }).containsExactly(jun(1), jul(1)).inOrder()
        assertThat(pending.first().amount.toPlainString()).isEqualTo("20000.00")
        assertThat(pending.first().type).isEqualTo(TransactionType.INCOME)
        assertThat(pending.first().occurrenceId)
            .isEqualTo(DeterministicUuid.v5("r:2026-06-01").toString())
    }

    @Test
    fun autoPostRule_isNeverPending() = runTest {
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun(1), autoPost = true)
        assertThat(useCase().first()).isEmpty()
    }

    @Test
    fun pausedRule_isNeverPending() = runTest {
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun(1), isPaused = true, autoPost = false)
        assertThat(useCase().first()).isEmpty()
    }

    @Test
    fun alreadyMaterializedOccurrence_isExcluded_evenIfCursorHasntMoved() = runTest {
        ruleDao.store["r"] = ruleEntity("r", frequency = RecurringFrequency.MONTHLY, nextDate = jun(1))
        // Jun 1 was already confirmed (its deterministic occurrence exists), cursor still at Jun 1.
        val junId = DeterministicUuid.v5("r:2026-06-01").toString()
        txnDao.store[junId] = transactionEntity(id = junId, recurringRuleId = "r")

        val pending = useCase().first()

        assertThat(pending.map { it.date }).containsExactly(jul(1)) // Jun 1 filtered out
    }

    @Test
    fun tombstonedOccurrence_isAlsoExcluded() = runTest {
        // Confirmed then deleted — the user decided; don't re-prompt.
        ruleDao.store["r"] = ruleEntity("r", frequency = RecurringFrequency.MONTHLY, nextDate = jun(1))
        val junId = DeterministicUuid.v5("r:2026-06-01").toString()
        txnDao.store[junId] = transactionEntity(id = junId, recurringRuleId = "r", isDeleted = true)

        assertThat(useCase().first().map { it.date }).containsExactly(jul(1))
    }

    @Test
    fun floor_autoSkipsOccurrencesOlderThanThreeMonths() = runTest {
        // Cursor parked back in January; today Jul 15 → floor is Apr 15. Jan–Apr occurrences are
        // auto-skipped (never surfaced); only May/Jun/Jul remain.
        ruleDao.store["r"] = ruleEntity(
            "r", frequency = RecurringFrequency.MONTHLY, nextDate = LocalDate.of(2026, 1, 1),
        )

        val pending = useCase().first()

        assertThat(pending.map { it.date }).containsExactly(
            LocalDate.of(2026, 5, 1), jun(1), jul(1),
        ).inOrder()
    }

    @Test
    fun futureRule_hasNoPending() = runTest {
        ruleDao.store["r"] = ruleEntity("r", nextDate = LocalDate.of(2026, 8, 1))
        assertThat(useCase().first()).isEmpty()
    }

    @Test
    fun ruleWithMissingCategory_isSkipped() = runTest {
        categories = emptyList()
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun(1))
        assertThat(useCase().first()).isEmpty()
    }

    @Test
    fun multipleRules_areMergedOldestFirstAcrossRules() = runTest {
        categories = listOf(
            category("cat-1", CategoryType.INCOME),
            category("cat-2", CategoryType.EXPENSE),
        )
        ruleDao.store["salary"] =
            ruleEntity("salary", frequency = RecurringFrequency.MONTHLY, nextDate = jul(1), categoryId = "cat-1")
        ruleDao.store["rent"] =
            ruleEntity("rent", frequency = RecurringFrequency.MONTHLY, nextDate = jun(20), categoryId = "cat-2")

        val pending = useCase().first()

        // Rent (Jun 20) precedes salary (Jul 1) even though its rule was added second; each rule
        // has a single occurrence in the window (today is Jul 15, so neither hits a boundary).
        assertThat(pending.map { it.ruleId }).containsExactly("rent", "salary").inOrder()
        assertThat(pending.map { it.date }).containsExactly(jun(20), jul(1)).inOrder()
    }

    private fun jun(day: Int) = LocalDate.of(2026, 6, day)
    private fun jul(day: Int) = LocalDate.of(2026, 7, day)
}
