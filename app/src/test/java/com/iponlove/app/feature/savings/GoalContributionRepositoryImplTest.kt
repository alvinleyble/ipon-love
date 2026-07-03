package com.iponlove.app.feature.savings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.savings.data.GoalContributionRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class GoalContributionRepositoryImplTest {

    private val dao = FakeGoalContributionDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val repository = GoalContributionRepositoryImpl(dao, clock, currentUser)

    @Test
    fun add_stampsContributorAndSyncColumns() = runTest {
        repository.addContribution("g-1", BigDecimal("500.00"), Instant.ofEpochMilli(2_000), "birthday money")

        val row = dao.store.values.single()
        assertThat(row.goalId).isEqualTo("g-1")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.amount).isEqualTo(BigDecimal("500.00"))
        assertThat(row.note).isEqualTo("birthday money")
        assertThat(row.pendingSync).isTrue()
        assertThat(row.serverRev).isNull()
        assertThat(row.id).isNotEmpty()
    }

    @Test
    fun add_twice_mintsDistinctIds_neverClobbers() = runTest {
        // The LWW-safety crux: every add is a brand-new random id, so two contributions of the
        // same amount to the same goal are two rows, never one overwriting the other.
        repository.addContribution("g-1", BigDecimal("500.00"), Instant.ofEpochMilli(2_000), null)
        repository.addContribution("g-1", BigDecimal("500.00"), Instant.ofEpochMilli(2_001), null)

        assertThat(dao.store).hasSize(2)
        val ids = dao.store.keys.toList()
        assertThat(ids[0]).isNotEqualTo(ids[1])
    }

    @Test
    fun edit_own_updatesFields_marksDirty() = runTest {
        dao.store["c"] = goalContributionEntity(
            id = "c", userId = "user-1", amount = BigDecimal("100.00"),
            updatedAt = Instant.ofEpochMilli(10_000),
        )
        now = Instant.ofEpochMilli(10_000)

        repository.editContribution("c", BigDecimal("250.00"), Instant.ofEpochMilli(5_000), "corrected")

        val row = dao.store.getValue("c")
        assertThat(row.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(row.note).isEqualTo("corrected")
        assertThat(row.updatedAt).isEqualTo(Instant.ofEpochMilli(10_001))
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun edit_partnerRow_isNoOp() = runTest {
        dao.store["c"] = goalContributionEntity(id = "c", userId = "partner-1", amount = BigDecimal("100.00"))

        repository.editContribution("c", BigDecimal("999.00"), Instant.ofEpochMilli(5_000), null)

        assertThat(dao.store.getValue("c").amount).isEqualTo(BigDecimal("100.00"))
    }

    @Test
    fun delete_own_isSoft() = runTest {
        dao.store["c"] = goalContributionEntity(id = "c", userId = "user-1")

        repository.deleteContribution("c")

        val row = dao.store.getValue("c")
        assertThat(row.isDeleted).isTrue()
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun softDeleteOwnForGoal_deletesOnlyMyRowsForThatGoal() = runTest {
        dao.store["mine-1"] = goalContributionEntity(id = "mine-1", goalId = "g-1", userId = "user-1")
        dao.store["mine-2"] = goalContributionEntity(id = "mine-2", goalId = "g-1", userId = "user-1")
        dao.store["partner"] = goalContributionEntity(id = "partner", goalId = "g-1", userId = "partner-1")
        dao.store["other-goal"] = goalContributionEntity(id = "other-goal", goalId = "g-2", userId = "user-1")

        repository.softDeleteOwnForGoal("g-1")

        assertThat(dao.store.getValue("mine-1").isDeleted).isTrue()
        assertThat(dao.store.getValue("mine-2").isDeleted).isTrue()
        assertThat(dao.store.getValue("partner").isDeleted).isFalse()   // RLS: not mine to delete
        assertThat(dao.store.getValue("other-goal").isDeleted).isFalse() // different goal
    }

    @Test
    fun purgePartnerData_removesReplicatedPartnerRows_keepsOwn() = runTest {
        dao.store["mine"] = goalContributionEntity(id = "mine", userId = "user-1")
        dao.store["theirs"] = goalContributionEntity(id = "theirs", userId = "partner-1")

        repository.purgePartnerData("user-1")

        assertThat(dao.store.keys).containsExactly("mine")
    }
}
