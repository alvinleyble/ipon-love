package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.util.DeterministicUuid
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.recurring.data.RecurringRuleRepositoryImpl
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.ConfirmOccurrenceUseCase
import com.iponlove.app.feature.transactions.FakeTransactionDao
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ConfirmOccurrenceUseCaseTest {

    private val ruleDao = FakeRecurringRuleDao()
    private val txnDao = FakeTransactionDao()
    private val clock = SyncClock(now = { Instant.ofEpochMilli(1_000) })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val ruleRepository = RecurringRuleRepositoryImpl(ruleDao, clock, currentUser)
    private val txnRepository = TransactionRepositoryImpl(txnDao, clock, currentUser)

    private var categories = listOf(category("cat-1", CategoryType.INCOME))
    private val observeCategories = ObserveCategoriesUseCase(FakeCategoryRepository { categories })

    private val confirm = ConfirmOccurrenceUseCase(ruleRepository, txnRepository, observeCategories)

    private val jun1 = LocalDate.of(2026, 6, 1)
    private val expectedId = DeterministicUuid.v5("r:2026-06-01").toString()

    @Test
    fun materializes_withDeterministicId_resolvedType_provenance_andRuleAmount() = runTest {
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun1, amount = "20000.00")

        confirm("r", jun1, amountOverride = null)

        val row = txnDao.store.getValue(expectedId)
        assertThat(row.type).isEqualTo(TransactionType.INCOME)
        assertThat(row.recurringRuleId).isEqualTo("r")
        assertThat(row.amount.toPlainString()).isEqualTo("20000.00")
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun amountOverride_appliesToThisOccurrenceOnly_ruleTemplateUnchanged() = runTest {
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun1, amount = "20000.00")

        confirm("r", jun1, amountOverride = BigDecimal("19450.00"))

        assertThat(txnDao.store.getValue(expectedId).amount.toPlainString()).isEqualTo("19450.00")
        // The rule's template amount is untouched — next month still pre-fills 20,000.
        assertThat(ruleRepository.getRule("r")!!.template.amount.toPlainString()).isEqualTo("20000.00")
    }

    @Test
    fun cursor_isNotAdvanced_onConfirm() = runTest {
        // Confirm parks the cursor (Item 37): "pending" drops the occurrence via the materialized
        // set, not by moving next_date — so out-of-order confirms can't strand earlier ones.
        ruleDao.store["r"] = ruleEntity("r", frequency = RecurringFrequency.MONTHLY, nextDate = jun1)

        confirm("r", jun1, amountOverride = null)

        assertThat(ruleRepository.getRule("r")!!.nextDate).isEqualTo(jun1)
    }

    @Test
    fun doubleConfirm_isIdempotent_noDuplicate() = runTest {
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun1, amount = "20000.00")

        confirm("r", jun1, amountOverride = BigDecimal("19450.00"))
        // A second confirm (double tap / another device) must not overwrite or duplicate.
        confirm("r", jun1, amountOverride = BigDecimal("1.00"))

        assertThat(txnDao.store).hasSize(1)
        assertThat(txnDao.store.getValue(expectedId).amount.toPlainString()).isEqualTo("19450.00")
    }

    @Test
    fun missingCategory_writesNothing() = runTest {
        categories = emptyList()
        ruleDao.store["r"] = ruleEntity("r", nextDate = jun1)

        confirm("r", jun1, amountOverride = null)

        assertThat(txnDao.store).isEmpty()
    }

    @Test
    fun unknownRule_isNoOp() = runTest {
        confirm("ghost", jun1, amountOverride = null)
        assertThat(txnDao.store).isEmpty()
    }
}
