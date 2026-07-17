package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.recurring.data.RecurringRuleRepositoryImpl
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.ObserveRecurringRulesUseCase
import com.iponlove.app.feature.recurring.domain.usecase.ObserveUpcomingUseCase
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ObserveUpcomingUseCaseTest {

    private val ruleDao = FakeRecurringRuleDao()
    private val clock = SyncClock(now = { Instant.ofEpochMilli(1_000) })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val ruleRepository = RecurringRuleRepositoryImpl(ruleDao, clock, currentUser)

    private var categories = listOf(
        category("cat-1", CategoryType.INCOME),
        category("cat-2", CategoryType.EXPENSE),
    )
    private val observeCategories = ObserveCategoriesUseCase(FakeCategoryRepository { categories })

    private val today = LocalDate.of(2026, 7, 15)
    private val observeUpcoming = ObserveUpcomingUseCase(
        observeRules = ObserveRecurringRulesUseCase(ruleRepository),
        observeCategories = observeCategories,
    )

    /** Invokes with the fixed test `today` and an inclusive window end at [end]. */
    private fun useCase(end: LocalDate) =
        observeUpcoming(windowEnd = { end }, today = { today })

    @Test
    fun surfacesFutureIncomeAndBills_withRuleAmountAndType() = runTest {
        // Salary due Jul 20 (income), rent due Jul 25 (expense); both after today (Jul 15).
        ruleDao.store["salary"] = ruleEntity("salary", nextDate = jul(20), amount = "20000.00", categoryId = "cat-1")
        ruleDao.store["rent"] = ruleEntity("rent", nextDate = jul(25), amount = "8000.00", categoryId = "cat-2")

        val upcoming = useCase(jul(31)).first()

        assertThat(upcoming.map { it.date }).containsExactly(jul(20), jul(25)).inOrder()
        assertThat(upcoming[0].type).isEqualTo(TransactionType.INCOME)
        assertThat(upcoming[0].amount.toPlainString()).isEqualTo("20000.00")
        assertThat(upcoming[1].type).isEqualTo(TransactionType.EXPENSE)
    }

    @Test
    fun todaysOccurrence_isExcluded_strictlyFutureOnly() = runTest {
        // Due exactly today — belongs to the ledger/"To confirm", not the forward preview.
        ruleDao.store["r"] = ruleEntity("r", nextDate = today, categoryId = "cat-1")
        assertThat(useCase(jul(31)).first()).isEmpty()
    }

    @Test
    fun pastOccurrence_isExcluded() = runTest {
        ruleDao.store["r"] = ruleEntity("r", nextDate = jul(10), categoryId = "cat-1")
        // Only the Jul 10 cursor is in the past; the next monthly hit (Aug 10) is past the window.
        assertThat(useCase(jul(31)).first()).isEmpty()
    }

    @Test
    fun windowEnd_trimsOccurrencesBeyondIt() = runTest {
        // Weekly rule: Jul 22 and Jul 29 land inside a Jul-31 window; Aug 5 does not.
        ruleDao.store["r"] = ruleEntity(
            "r", frequency = RecurringFrequency.WEEKLY, nextDate = jul(22), categoryId = "cat-1",
        )
        assertThat(useCase(jul(31)).first().map { it.date }).containsExactly(jul(22), jul(29)).inOrder()
    }

    @Test
    fun autoPostRule_isIncluded_unlikePending() = runTest {
        // The forecast covers ALL scheduled money — a silent auto-post rule still appears here.
        ruleDao.store["r"] = ruleEntity("r", nextDate = jul(20), autoPost = true, categoryId = "cat-1")
        assertThat(useCase(jul(31)).first().map { it.date }).containsExactly(jul(20))
    }

    @Test
    fun pausedRule_isExcluded() = runTest {
        ruleDao.store["r"] = ruleEntity("r", nextDate = jul(20), isPaused = true, categoryId = "cat-1")
        assertThat(useCase(jul(31)).first()).isEmpty()
    }

    @Test
    fun ruleWithMissingCategory_isSkipped() = runTest {
        categories = emptyList()
        ruleDao.store["r"] = ruleEntity("r", nextDate = jul(20))
        assertThat(useCase(jul(31)).first()).isEmpty()
    }

    @Test
    fun multipleRules_mergedOldestFirst() = runTest {
        ruleDao.store["rent"] = ruleEntity("rent", nextDate = jul(25), categoryId = "cat-2")
        ruleDao.store["salary"] = ruleEntity("salary", nextDate = jul(20), categoryId = "cat-1")
        assertThat(useCase(jul(31)).first().map { it.ruleId }).containsExactly("salary", "rent").inOrder()
    }

    private fun jul(day: Int) = LocalDate.of(2026, 7, day)
}
