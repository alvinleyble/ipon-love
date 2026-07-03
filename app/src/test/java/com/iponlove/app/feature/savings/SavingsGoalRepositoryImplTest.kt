package com.iponlove.app.feature.savings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.savings.data.SavingsGoalRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class SavingsGoalRepositoryImplTest {

    private val dao = FakeSavingsGoalDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val repository = SavingsGoalRepositoryImpl(dao, clock, currentUser)

    @Test
    fun upsert_newGoal_stampsOwnerAndSyncColumns() = runTest {
        repository.upsertGoal(savingsGoal("g", name = "Emergency fund", targetAmount = BigDecimal("5000.00")))

        val row = dao.store.getValue("g")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.name).isEqualTo("Emergency fund")
        assertThat(row.targetAmount).isEqualTo(BigDecimal("5000.00"))
        assertThat(row.isShared).isFalse()
        assertThat(row.coupleId).isNull()
        assertThat(row.pendingSync).isTrue()
        assertThat(row.serverRev).isNull()
    }

    @Test
    fun upsert_existing_preservesProvenanceAndSharing_monotonicUpdatedAt() = runTest {
        dao.store["g"] = savingsGoalEntity(
            id = "g", userId = "user-1", isShared = true, coupleId = "c-1",
            isArchived = true, createdAt = Instant.ofEpochMilli(1_000),
            updatedAt = Instant.ofEpochMilli(10_000), serverRev = 42,
        )
        now = Instant.ofEpochMilli(10_000)

        repository.upsertGoal(savingsGoal("g", name = "Renamed", targetAmount = BigDecimal("7000.00")))

        val row = dao.store.getValue("g")
        assertThat(row.name).isEqualTo("Renamed")
        assertThat(row.updatedAt).isEqualTo(Instant.ofEpochMilli(10_001)) // strict forward
        assertThat(row.isShared).isTrue()
        assertThat(row.coupleId).isEqualTo("c-1")
        assertThat(row.isArchived).isTrue()
        assertThat(row.createdAt).isEqualTo(Instant.ofEpochMilli(1_000))
        assertThat(row.serverRev).isEqualTo(42)
    }

    @Test
    fun upsert_partnerGoal_isNoOp() = runTest {
        dao.store["g"] = savingsGoalEntity(id = "g", userId = "partner-1", name = "Theirs")

        repository.upsertGoal(savingsGoal("g", name = "Hijacked"))

        assertThat(dao.store.getValue("g").name).isEqualTo("Theirs")
    }

    @Test
    fun shareGoal_setsSharedAndCoupleId_marksDirty() = runTest {
        dao.store["g"] = savingsGoalEntity(id = "g", isShared = false, coupleId = null)

        repository.shareGoal("g", "couple-9")

        val row = dao.store.getValue("g")
        assertThat(row.isShared).isTrue()
        assertThat(row.coupleId).isEqualTo("couple-9")
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun unshareGoal_clearsShared_retainsCoupleId() = runTest {
        dao.store["g"] = savingsGoalEntity(id = "g", isShared = true, coupleId = "couple-9")

        repository.unshareGoal("g")

        val row = dao.store.getValue("g")
        assertThat(row.isShared).isFalse()
        assertThat(row.coupleId).isEqualTo("couple-9") // retained so the un-share reaches the partner view
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun deleteGoal_isSoft_andPartnerGoalIsNoOp() = runTest {
        dao.store["mine"] = savingsGoalEntity(id = "mine", userId = "user-1")
        dao.store["theirs"] = savingsGoalEntity(id = "theirs", userId = "partner-1", isShared = true)

        repository.deleteGoal("mine")
        repository.deleteGoal("theirs")

        assertThat(dao.store.getValue("mine").isDeleted).isTrue()
        assertThat(dao.store.getValue("theirs").isDeleted).isFalse() // can't delete partner's goal
    }

    @Test
    fun purgePartnerData_removesReplicatedPartnerGoals_keepsOwn() = runTest {
        dao.store["mine"] = savingsGoalEntity(id = "mine", userId = "user-1")
        dao.store["theirs"] = savingsGoalEntity(id = "theirs", userId = "partner-1", isShared = true)

        repository.purgePartnerData("user-1")

        assertThat(dao.store.keys).containsExactly("mine")
    }
}
