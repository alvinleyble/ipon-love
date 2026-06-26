package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.util.DeterministicUuid
import com.iponlove.app.feature.partnerdebt.domain.usecase.DebtNettingCalculator
import java.math.BigDecimal
import java.time.Instant
import org.junit.Test

/**
 * Covers: basic netting, multi-debt FIFO, partially-settled opposing debt, already-settled
 * opposing debt skipped, same-direction debt ignored, offline-convergence (deterministic IDs),
 * empty result when no opposing debts.
 */
class DebtNettingCalculatorTest {

    private val now = Instant.ofEpochMilli(10_000)

    // Helper: netting payment id as the calculator would produce.
    private fun nettingId(debtId: String, counterDebtId: String) =
        DeterministicUuid.v5("netting:$debtId:$counterDebtId").toString()

    @Test
    fun noOpposingDebts_returnsEmpty() {
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "1000.00")
        val existing = listOf(
            partnerDebt("d-same", borrowerId = "A", lenderId = "B", amount = "500.00"),
        )
        val result = DebtNettingCalculator.computeNetting(newDebt, existing, emptyList(), now)
        assertThat(result.payments).isEmpty()
    }

    @Test
    fun basicNet_newDebtSmallerThanOpposing_settlesNewDebtFully() {
        // New: A owes B ₱1k.  Existing: B owes A ₱3k.
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "1000.00",
            createdAt = Instant.ofEpochMilli(2_000))
        val existing = partnerDebt("d-old", borrowerId = "B", lenderId = "A", amount = "3000.00",
            createdAt = Instant.ofEpochMilli(1_000))

        val result = DebtNettingCalculator.computeNetting(newDebt, listOf(existing), emptyList(), now)

        assertThat(result.payments).hasSize(2)
        val onNew = result.payments.single { it.debtId == "d-new" }
        val onOld = result.payments.single { it.debtId == "d-old" }

        assertThat(onNew.amount).isEqualTo(BigDecimal("1000.00"))
        assertThat(onNew.isNetting).isTrue()
        assertThat(onNew.counterDebtId).isEqualTo("d-old")
        assertThat(onOld.amount).isEqualTo(BigDecimal("1000.00"))
        assertThat(onOld.isNetting).isTrue()
        assertThat(onOld.counterDebtId).isEqualTo("d-new")
    }

    @Test
    fun basicNet_newDebtLargerThanOpposing_partiallySettled() {
        // New: A owes B ₱3k.  Existing: B owes A ₱1k.
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "3000.00",
            createdAt = Instant.ofEpochMilli(2_000))
        val existing = partnerDebt("d-old", borrowerId = "B", lenderId = "A", amount = "1000.00",
            createdAt = Instant.ofEpochMilli(1_000))

        val result = DebtNettingCalculator.computeNetting(newDebt, listOf(existing), emptyList(), now)

        assertThat(result.payments).hasSize(2)
        assertThat(result.payments.single { it.debtId == "d-new" }.amount).isEqualTo(BigDecimal("1000.00"))
        assertThat(result.payments.single { it.debtId == "d-old" }.amount).isEqualTo(BigDecimal("1000.00"))
    }

    @Test
    fun multidebtFifo_olderDebtOffsetFirst() {
        // New: A owes B ₱1200.  Two opposing debts: d-old1 ₱800 (older), d-old2 ₱900 (newer).
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "1200.00",
            createdAt = Instant.ofEpochMilli(5_000))
        val d1 = partnerDebt("d-old1", borrowerId = "B", lenderId = "A", amount = "800.00",
            createdAt = Instant.ofEpochMilli(1_000))
        val d2 = partnerDebt("d-old2", borrowerId = "B", lenderId = "A", amount = "900.00",
            createdAt = Instant.ofEpochMilli(2_000))

        val result = DebtNettingCalculator.computeNetting(newDebt, listOf(d2, d1), emptyList(), now)

        // d-old1 fully offset (800), then d-old2 partially offset (400) to exhaust d-new.
        val byDebt = result.payments.associateBy { it.debtId }
        assertThat(byDebt.getValue("d-old1").amount).isEqualTo(BigDecimal("800.00"))
        assertThat(byDebt.getValue("d-old2").amount).isEqualTo(BigDecimal("400.00"))
        // Payment on d-new = 800 + 400 = 1200 (two payments).
        val onNew = result.payments.filter { it.debtId == "d-new" }
        val totalOnNew = onNew.fold(BigDecimal.ZERO) { acc, p -> acc + p.amount }
        assertThat(totalOnNew).isEqualTo(BigDecimal("1200.00"))
    }

    @Test
    fun partiallySettledOpposing_onlyRemainingRemainingIsOffset() {
        // Existing opposing debt d-old ₱1000, already has ₱700 payment → ₱300 remaining.
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "500.00",
            createdAt = Instant.ofEpochMilli(2_000))
        val existing = partnerDebt("d-old", borrowerId = "B", lenderId = "A", amount = "1000.00",
            createdAt = Instant.ofEpochMilli(1_000))
        val existingPayment = debtPayment("p-manual", debtId = "d-old", amount = "700.00")

        val result = DebtNettingCalculator.computeNetting(newDebt, listOf(existing), listOf(existingPayment), now)

        // offset = min(500, 300) = 300
        assertThat(result.payments.single { it.debtId == "d-new" }.amount).isEqualTo(BigDecimal("300.00"))
        assertThat(result.payments.single { it.debtId == "d-old" }.amount).isEqualTo(BigDecimal("300.00"))
    }

    @Test
    fun fullySettledOpposing_isSkipped() {
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "500.00",
            createdAt = Instant.ofEpochMilli(2_000))
        val existing = partnerDebt("d-old", borrowerId = "B", lenderId = "A", amount = "1000.00",
            createdAt = Instant.ofEpochMilli(1_000))
        val fullPayment = debtPayment("p-full", debtId = "d-old", amount = "1000.00")

        val result = DebtNettingCalculator.computeNetting(newDebt, listOf(existing), listOf(fullPayment), now)

        assertThat(result.payments).isEmpty()
    }

    @Test
    fun deterministicIds_sameInputsProduceSamePaymentIds() {
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "1000.00")
        val existing = partnerDebt("d-old", borrowerId = "B", lenderId = "A", amount = "1000.00")

        val r1 = DebtNettingCalculator.computeNetting(newDebt, listOf(existing), emptyList(), now)
        val r2 = DebtNettingCalculator.computeNetting(newDebt, listOf(existing), emptyList(), now)

        assertThat(r1.payments.map { it.id }).isEqualTo(r2.payments.map { it.id })
    }

    @Test
    fun offlineConvergence_swappedRoles_producesSamePaymentIds() {
        // Device 1: thinks d-A is "new", d-B is "existing".
        // Device 2: thinks d-B is "new", d-A is "existing" (both created offline).
        val dA = partnerDebt("d-A", borrowerId = "A", lenderId = "B", amount = "500.00",
            createdAt = Instant.ofEpochMilli(1_000))
        val dB = partnerDebt("d-B", borrowerId = "B", lenderId = "A", amount = "500.00",
            createdAt = Instant.ofEpochMilli(2_000))

        val fromDevice1 = DebtNettingCalculator.computeNetting(dB, listOf(dA), emptyList(), now)
        val fromDevice2 = DebtNettingCalculator.computeNetting(dA, listOf(dB), emptyList(), now)

        // Both produce the same two payment IDs.
        assertThat(fromDevice1.payments.map { it.id }.toSet())
            .isEqualTo(fromDevice2.payments.map { it.id }.toSet())

        // Expected IDs.
        val expectedOnA = nettingId("d-A", "d-B")
        val expectedOnB = nettingId("d-B", "d-A")
        assertThat(fromDevice1.payments.map { it.id })
            .containsExactly(expectedOnB, expectedOnA)
        assertThat(fromDevice2.payments.map { it.id })
            .containsExactly(expectedOnA, expectedOnB)
    }

    @Test
    fun nettingPaymentIds_matchExpectedDeterministicPattern() {
        val newDebt = partnerDebt("d-new", borrowerId = "A", lenderId = "B", amount = "100.00")
        val existing = partnerDebt("d-old", borrowerId = "B", lenderId = "A", amount = "100.00")

        val result = DebtNettingCalculator.computeNetting(newDebt, listOf(existing), emptyList(), now)

        val onNew = result.payments.single { it.debtId == "d-new" }
        val onOld = result.payments.single { it.debtId == "d-old" }
        assertThat(onNew.id).isEqualTo(nettingId("d-new", "d-old"))
        assertThat(onOld.id).isEqualTo(nettingId("d-old", "d-new"))
    }
}
