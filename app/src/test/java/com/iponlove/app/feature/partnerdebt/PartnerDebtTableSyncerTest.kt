package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.partnerdebt.data.remote.DebtPaymentDto
import com.iponlove.app.feature.partnerdebt.data.remote.DebtPaymentRemoteSource
import com.iponlove.app.feature.partnerdebt.data.remote.PartnerDebtDto
import com.iponlove.app.feature.partnerdebt.data.remote.PartnerDebtRemoteSource
import com.iponlove.app.feature.partnerdebt.data.sync.DebtPaymentTableSyncer
import com.iponlove.app.feature.partnerdebt.data.sync.PartnerDebtTableSyncer
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PartnerDebtTableSyncerTest {

    private class FakeDebtRemote : PartnerDebtRemoteSource {
        val pushed = mutableListOf<PartnerDebtDto>()
        val serverRows = mutableListOf<PartnerDebtDto>()
        override suspend fun push(rows: List<PartnerDebtDto>): List<String> {
            pushed += rows
            return rows.map { it.id }
        }
        override suspend fun pull(cursor: Long, limit: Int): List<PartnerDebtDto> =
            serverRows.filter { (it.serverRev ?: 0L) > cursor }.sortedBy { it.serverRev }.take(limit)
    }

    private class FakePaymentRemote : DebtPaymentRemoteSource {
        val pushed = mutableListOf<DebtPaymentDto>()
        val serverRows = mutableListOf<DebtPaymentDto>()
        override suspend fun push(rows: List<DebtPaymentDto>): List<String> {
            pushed += rows
            return rows.map { it.id }
        }
        override suspend fun pull(cursor: Long, limit: Int): List<DebtPaymentDto> =
            serverRows.filter { (it.serverRev ?: 0L) > cursor }.sortedBy { it.serverRev }.take(limit)
    }

    private val dao = FakePartnerDebtDao()
    private val debtRemote = FakeDebtRemote()
    private val paymentRemote = FakePaymentRemote()
    private val cursors = InMemoryCursorStore()
    private val currentUser = CurrentUserProvider { "me" }
    private val debtSyncer = PartnerDebtTableSyncer(dao, debtRemote, currentUser, cursors, ConflictResolver())
    private val paymentSyncer = DebtPaymentTableSyncer(dao, paymentRemote, currentUser, cursors, ConflictResolver())

    @Test
    fun syncers_useTheirOwnTables() {
        assertThat(debtSyncer.table).isEqualTo(SyncTable.PARTNER_DEBTS)
        assertThat(paymentSyncer.table).isEqualTo(SyncTable.DEBT_PAYMENTS)
        // FK order: a debt's couple parents come first, then the debt, then its payments.
        assertThat(SyncTable.COUPLES.ordinal).isLessThan(SyncTable.PARTNER_DEBTS.ordinal)
        assertThat(SyncTable.PARTNER_DEBTS.ordinal).isLessThan(SyncTable.DEBT_PAYMENTS.ordinal)
    }

    @Test
    fun debt_push_mapsDirtyRows_andClearsAckedFlag() = runTest {
        dao.debts["a"] = partnerDebtEntity(id = "a", pendingSync = true)
        dao.debts["b"] = partnerDebtEntity(id = "b", pendingSync = false)

        debtSyncer.push()

        assertThat(debtRemote.pushed.map { it.id }).containsExactly("a")
        assertThat(dao.debts.getValue("a").pendingSync).isFalse()
    }

    @Test
    fun debt_pull_mapsRemoteRows_andAdvancesCursor() = runTest {
        debtRemote.serverRows += partnerDebtDto(id = "a", amount = "1234.00", serverRev = 12)

        debtSyncer.pull()

        val row = dao.debts.getValue("a")
        assertThat(row.amount.toPlainString()).isEqualTo("1234.00")
        assertThat(row.pendingSync).isFalse()
        assertThat(cursors.cursor(SyncTable.PARTNER_DEBTS)).isEqualTo(12)
    }

    @Test
    fun payment_pull_mapsRemoteRows_andAdvancesItsOwnCursor() = runTest {
        paymentRemote.serverRows += debtPaymentDto(id = "p", debtId = "d", amount = "99.00", serverRev = 20)

        paymentSyncer.pull()

        val row = dao.payments.getValue("p")
        assertThat(row.amount.toPlainString()).isEqualTo("99.00")
        assertThat(cursors.cursor(SyncTable.DEBT_PAYMENTS)).isEqualTo(20)
    }

    // ---- push ownership filter (v1.6.5 Item 20 follow-up) ----

    @Test
    fun debt_push_skipsStaleCoupleRow_soItCantWedgeTheBatch() = runTest {
        dao.currentCoupleId = "c-1"
        dao.debts["a"] = partnerDebtEntity(id = "a", coupleId = "c-1", pendingSync = true)
        dao.debts["stale"] = partnerDebtEntity(id = "stale", coupleId = "old-couple", pendingSync = true)

        debtSyncer.push()

        assertThat(debtRemote.pushed.map { it.id }).containsExactly("a")
        assertThat(dao.debts.getValue("stale").pendingSync).isTrue() // benign orphan, still local
    }

    @Test
    fun debt_push_whenUnpaired_skipsEveryCoupleRow() = runTest {
        dao.currentCoupleId = null
        dao.debts["a"] = partnerDebtEntity(id = "a", coupleId = "c-1", pendingSync = true)

        assertThat(debtSyncer.push()).isFalse()
        assertThat(debtRemote.pushed).isEmpty()
    }

    @Test
    fun payment_push_onlyForDebtsInCurrentCouple() = runTest {
        dao.currentCoupleId = "c-1"
        dao.debts["mine"] = partnerDebtEntity(id = "mine", coupleId = "c-1")
        dao.debts["stale"] = partnerDebtEntity(id = "stale", coupleId = "old-couple")
        dao.payments["p-ok"] = debtPaymentEntity(id = "p-ok", debtId = "mine", pendingSync = true)
        dao.payments["p-stale"] = debtPaymentEntity(id = "p-stale", debtId = "stale", pendingSync = true)
        dao.payments["p-orphan"] = debtPaymentEntity(id = "p-orphan", debtId = "absent", pendingSync = true)

        paymentSyncer.push()

        assertThat(paymentRemote.pushed.map { it.id }).containsExactly("p-ok")
        assertThat(dao.payments.getValue("p-ok").pendingSync).isFalse()
        assertThat(dao.payments.getValue("p-stale").pendingSync).isTrue()
        assertThat(dao.payments.getValue("p-orphan").pendingSync).isTrue()
    }

    @Test
    fun payment_push_nettingRow_keysOffParentDebt_notCounterDebt() = runTest {
        // A netting payment references debtId (its parent) + counterDebtId (the opposing debt);
        // RLS gates on debt_id only, so pushability keys off the parent — netting stays intact
        // even when the counter-debt id resolves to no local row.
        dao.currentCoupleId = "c-1"
        dao.debts["mine"] = partnerDebtEntity(id = "mine", coupleId = "c-1")
        dao.payments["net"] = debtPaymentEntity(
            id = "net", debtId = "mine", counterDebtId = "absent-counter",
            isNetting = true, pendingSync = true,
        )

        paymentSyncer.push()

        assertThat(paymentRemote.pushed.map { it.id }).containsExactly("net")
    }

    @Test
    fun payment_push_whenUnpaired_skipsEverything() = runTest {
        dao.currentCoupleId = null
        dao.debts["mine"] = partnerDebtEntity(id = "mine", coupleId = "c-1")
        dao.payments["p"] = debtPaymentEntity(id = "p", debtId = "mine", pendingSync = true)

        assertThat(paymentSyncer.push()).isFalse()
        assertThat(paymentRemote.pushed).isEmpty()
    }
}
