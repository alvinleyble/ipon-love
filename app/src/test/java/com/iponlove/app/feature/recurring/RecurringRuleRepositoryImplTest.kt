package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.recurring.data.RecurringRuleRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RecurringRuleRepositoryImplTest {

    private val dao = FakeRecurringRuleDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val repository = RecurringRuleRepositoryImpl(dao, clock, currentUser)

    @Test
    fun upsert_newRule_stampsOwnerAndSyncColumns() = runTest {
        repository.upsertRule(rule("r", amount = "2500.00"))

        val row = dao.store.getValue("r")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.templateAmount.toPlainString()).isEqualTo("2500.00")
        assertThat(row.pendingSync).isTrue()
        assertThat(row.serverRev).isNull()
        assertThat(row.updatedAt).isEqualTo(now)
    }

    @Test
    fun upsert_existing_advancesUpdatedAtMonotonically_preservesProvenance() = runTest {
        dao.store["r"] = ruleEntity(
            id = "r",
            userId = "owner-x",
            createdAt = Instant.ofEpochMilli(1_000),
            updatedAt = Instant.ofEpochMilli(10_000),
            serverRev = 55,
        )
        now = Instant.ofEpochMilli(10_000)

        // The materialization pass advancing the cursor is just another upsert.
        repository.upsertRule(rule("r", nextDate = LocalDate.of(2026, 7, 1)))

        val row = dao.store.getValue("r")
        assertThat(row.nextDate).isEqualTo(LocalDate.of(2026, 7, 1))
        assertThat(row.pendingSync).isTrue()
        assertThat(row.updatedAt).isEqualTo(Instant.ofEpochMilli(10_001))
        assertThat(row.userId).isEqualTo("owner-x")
        assertThat(row.createdAt).isEqualTo(Instant.ofEpochMilli(1_000))
        assertThat(row.serverRev).isEqualTo(55)
    }

    @Test
    fun activeRules_excludesTombstones() = runTest {
        dao.store["a"] = ruleEntity(id = "a")
        dao.store["b"] = ruleEntity(id = "b", isDeleted = true)

        assertThat(repository.activeRules().map { it.id }).containsExactly("a")
    }

    @Test
    fun delete_isSoft_setsTombstoneAndMarksDirty() = runTest {
        dao.store["r"] = ruleEntity(id = "r", serverRev = 3)

        repository.deleteRule("r")

        val row = dao.store.getValue("r")
        assertThat(row.isDeleted).isTrue()
        assertThat(row.pendingSync).isTrue()
    }
}
