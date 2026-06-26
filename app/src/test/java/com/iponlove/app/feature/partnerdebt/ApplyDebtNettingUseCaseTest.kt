package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtRepositoryImpl
import com.iponlove.app.feature.partnerdebt.domain.usecase.ApplyDebtNettingUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/**
 * Integration-level tests for [ApplyDebtNettingUseCase] wired to the fake DAO.
 * Covers: netting fired on create, multi-debt FIFO, delete-debt removes netting payments,
 * idempotency (re-running produces same deterministic IDs without duplication).
 */
class ApplyDebtNettingUseCaseTest {

    private val dao = FakePartnerDebtDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val repository = PartnerDebtRepositoryImpl(dao, clock)
    private val useCase = ApplyDebtNettingUseCase(repository, clock)

    @Test
    fun noOpposingDebts_noPaymentsCreated() = runTest {
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B")
        dao.debts["d-same"] = partnerDebtEntity(
            id = "d-same", coupleId = "c-1", borrowerId = "A", lenderId = "B",
        )

        useCase(newDebt, coupleId = "c-1")

        assertThat(dao.payments).isEmpty()
    }

    @Test
    fun basicNet_createsTwoNettingPayments() = runTest {
        // Seed an opposing debt in the DAO (already persisted).
        dao.debts["d-old"] = partnerDebtEntity(
            id = "d-old", coupleId = "c-1",
            borrowerId = "B", lenderId = "A", amount = "3000.00",
            createdAt = Instant.ofEpochMilli(1_000),
        )
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "1000.00")

        useCase(newDebt, coupleId = "c-1")

        // Two netting payments created.
        assertThat(dao.payments).hasSize(2)
        val onNew = dao.payments.values.single { it.debtId == "d-new" }
        val onOld = dao.payments.values.single { it.debtId == "d-old" }
        assertThat(onNew.isNetting).isTrue()
        assertThat(onNew.counterDebtId).isEqualTo("d-old")
        assertThat(onOld.isNetting).isTrue()
        assertThat(onOld.counterDebtId).isEqualTo("d-new")
        // Offset = min(1000, 3000) = 1000.
        assertThat(onNew.amount.toPlainString()).isEqualTo("1000.00")
        assertThat(onOld.amount.toPlainString()).isEqualTo("1000.00")
        // Both are pending sync.
        assertThat(onNew.pendingSync).isTrue()
        assertThat(onOld.pendingSync).isTrue()
    }

    @Test
    fun idempotent_rerunProducesSameIds_noExtraPayments() = runTest {
        dao.debts["d-old"] = partnerDebtEntity(
            id = "d-old", coupleId = "c-1",
            borrowerId = "B", lenderId = "A", amount = "1000.00",
        )
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "1000.00")

        useCase(newDebt, coupleId = "c-1")
        val idsAfterFirst = dao.payments.keys.toSet()

        useCase(newDebt, coupleId = "c-1")

        // Running again upserts the same IDs — no new rows.
        assertThat(dao.payments.keys.toSet()).isEqualTo(idsAfterFirst)
        assertThat(dao.payments).hasSize(2)
    }

    @Test
    fun deleteDebt_removesNettingPayments_onBothSides() = runTest {
        // Set up two opposing debts and their netting payments.
        dao.debts["d-old"] = partnerDebtEntity(
            id = "d-old", coupleId = "c-1",
            borrowerId = "B", lenderId = "A", amount = "1000.00",
        )
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "1000.00")
        // Persist new debt too so delete can find it.
        dao.debts["d-new"] = partnerDebtEntity(
            id = "d-new", coupleId = "c-1",
            borrowerId = "A", lenderId = "B", amount = "1000.00",
        )
        useCase(newDebt, coupleId = "c-1")
        assertThat(dao.payments).hasSize(2)

        // Delete d-new — should cascade to its netting payments on both sides.
        repository.deleteDebt("d-new")

        val activePayments = dao.payments.values.filter { !it.isDeleted }
        assertThat(activePayments).isEmpty()
        // Soft-delete tombstones remain (ADR-0010).
        assertThat(dao.payments).hasSize(2)
        assertThat(dao.payments.values.all { it.isDeleted }).isTrue()
        assertThat(dao.payments.values.all { it.pendingSync }).isTrue()
    }

    @Test
    fun deleteOpposingDebt_alsoRemovesPaymentOnNewDebt() = runTest {
        dao.debts["d-old"] = partnerDebtEntity(
            id = "d-old", coupleId = "c-1",
            borrowerId = "B", lenderId = "A", amount = "1000.00",
        )
        dao.debts["d-new"] = partnerDebtEntity(
            id = "d-new", coupleId = "c-1",
            borrowerId = "A", lenderId = "B", amount = "1000.00",
        )
        useCase(
            partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "1000.00"),
            coupleId = "c-1",
        )

        // Deleting d-old should soft-delete the netting payment ON d-new that references d-old.
        repository.deleteDebt("d-old")

        val activePayments = dao.payments.values.filter { !it.isDeleted }
        assertThat(activePayments).isEmpty()
    }

    @Test
    fun multidebtFifo_olderOpposingDebtOffsetFirst() = runTest {
        dao.debts["d-old1"] = partnerDebtEntity(
            id = "d-old1", coupleId = "c-1",
            borrowerId = "B", lenderId = "A", amount = "800.00",
            createdAt = Instant.ofEpochMilli(1_000),
        )
        dao.debts["d-old2"] = partnerDebtEntity(
            id = "d-old2", coupleId = "c-1",
            borrowerId = "B", lenderId = "A", amount = "900.00",
            createdAt = Instant.ofEpochMilli(2_000),
        )
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "1200.00")

        useCase(newDebt, coupleId = "c-1")

        // 4 payments: two per pair.
        val onOld1 = dao.payments.values.single { it.debtId == "d-old1" }
        val onOld2 = dao.payments.values.single { it.debtId == "d-old2" }
        assertThat(onOld1.amount.toPlainString()).isEqualTo("800.00")   // d-old1 fully offset
        assertThat(onOld2.amount.toPlainString()).isEqualTo("400.00")   // only 400 of d-old2
    }
}
