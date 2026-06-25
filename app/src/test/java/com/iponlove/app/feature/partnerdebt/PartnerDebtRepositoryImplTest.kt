package com.iponlove.app.feature.partnerdebt

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class PartnerDebtRepositoryImplTest {

    private val dao = FakePartnerDebtDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val repository = PartnerDebtRepositoryImpl(dao, clock)

    @Test
    fun upsertDebt_new_stampsCoupleOwnershipAndSyncColumns() = runTest {
        repository.upsertDebt(partnerDebt("d", borrowerId = "me", lenderId = "you", amount = "1000.00"), coupleId = "c-1")

        val row = dao.debts.getValue("d")
        assertThat(row.coupleId).isEqualTo("c-1")
        assertThat(row.borrowerId).isEqualTo("me")
        assertThat(row.lenderId).isEqualTo("you")
        assertThat(row.pendingSync).isTrue()
        assertThat(row.serverRev).isNull()
        assertThat(row.updatedAt).isEqualTo(now)
        assertThat(row.createdAt).isEqualTo(now)
    }

    @Test
    fun upsertDebt_existing_preservesProvenance_advancesUpdatedAtMonotonically() = runTest {
        dao.debts["d"] = partnerDebtEntity(
            id = "d",
            coupleId = "c-1",
            createdAt = Instant.ofEpochMilli(1_000),
            updatedAt = Instant.ofEpochMilli(10_000),
            serverRev = 55,
        )
        now = Instant.ofEpochMilli(10_000)

        // Even if a caller passes a different couple, the row's existing ownership wins.
        repository.upsertDebt(partnerDebt("d", amount = "2000.00"), coupleId = "c-2")

        val row = dao.debts.getValue("d")
        assertThat(row.coupleId).isEqualTo("c-1")
        assertThat(row.amount.toPlainString()).isEqualTo("2000.00")
        assertThat(row.createdAt).isEqualTo(Instant.ofEpochMilli(1_000))
        assertThat(row.updatedAt).isEqualTo(Instant.ofEpochMilli(10_001))
        assertThat(row.serverRev).isEqualTo(55)
    }

    @Test
    fun deleteDebt_isSoft_setsTombstoneAndMarksDirty() = runTest {
        dao.debts["d"] = partnerDebtEntity(id = "d", serverRev = 3)

        repository.deleteDebt("d")

        val row = dao.debts.getValue("d")
        assertThat(row.isDeleted).isTrue()
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun upsertPayment_new_stampsSyncColumns_keepsUserDate() = runTest {
        repository.upsertPayment(debtPayment("p", debtId = "d", amount = "150.00", date = Instant.ofEpochMilli(7_777)))

        val row = dao.payments.getValue("p")
        assertThat(row.debtId).isEqualTo("d")
        assertThat(row.date).isEqualTo(Instant.ofEpochMilli(7_777))
        assertThat(row.pendingSync).isTrue()
        assertThat(row.updatedAt).isEqualTo(now)
    }

    @Test
    fun observeDebts_returnsOnlyActiveRowsForThatCouple() = runTest {
        dao.debts["ours"] = partnerDebtEntity(id = "ours", coupleId = "c-1")
        dao.debts["theirs"] = partnerDebtEntity(id = "theirs", coupleId = "c-2")
        dao.debts["gone"] = partnerDebtEntity(id = "gone", coupleId = "c-1", isDeleted = true)

        repository.observeDebts("c-1").test {
            assertThat(awaitItem().map { it.id }).containsExactly("ours")
        }
    }

    @Test
    fun purge_clearsBothTables() = runTest {
        dao.debts["d"] = partnerDebtEntity(id = "d")
        dao.payments["p"] = debtPaymentEntity(id = "p", debtId = "d")

        repository.purgeCoupleDebts()

        assertThat(dao.debts).isEmpty()
        assertThat(dao.payments).isEmpty()
    }
}
