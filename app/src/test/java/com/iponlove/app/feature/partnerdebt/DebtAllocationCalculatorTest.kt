package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.partnerdebt.domain.usecase.AllocationTarget
import com.iponlove.app.feature.partnerdebt.domain.usecase.DebtAllocationCalculator
import org.junit.Test
import java.math.BigDecimal

/**
 * The overpay cascade's money math (ADR-0055 #10): one lump split across the debts the user
 * ticked, filled in tick order, each floored at its own remaining. Pure and rounding-free —
 * a split must always sum back to exactly the lump.
 */
class DebtAllocationCalculatorTest {

    private fun target(id: String, remaining: String) = AllocationTarget(id, BigDecimal(remaining))

    private val threeDebts = listOf(
        target("d-1", "300.00"),
        target("d-2", "200.00"),
        target("d-3", "150.00"),
    )

    @Test
    fun ceiling_isTheCombinedRemaining() {
        assertThat(DebtAllocationCalculator.ceiling(threeDebts).toPlainString()).isEqualTo("650.00")
    }

    @Test
    fun ceiling_ofNothingIsZero() {
        assertThat(DebtAllocationCalculator.ceiling(emptyList()).signum()).isEqualTo(0)
    }

    @Test
    fun singleDebt_payingLessThanRemaining_isAnOrdinaryPartialPayment() {
        val split = DebtAllocationCalculator.allocate(listOf(target("d-1", "300.00")), BigDecimal("120.00"))

        assertThat(split).hasSize(1)
        assertThat(split.single().debtId).isEqualTo("d-1")
        assertThat(split.single().amount.toPlainString()).isEqualTo("120.00")
    }

    @Test
    fun fillsInTickOrder_flooringEachAtItsRemaining() {
        val split = DebtAllocationCalculator.allocate(threeDebts, BigDecimal("560.00"))

        // First two are cleared outright; the last touched takes the remainder.
        assertThat(split.map { it.debtId }).containsExactly("d-1", "d-2", "d-3").inOrder()
        assertThat(split.map { it.amount.toPlainString() })
            .containsExactly("300.00", "200.00", "60.00").inOrder()
    }

    @Test
    fun tickOrderIsTheFillOrder_notBoardOrder() {
        // Same three debts, ticked back-to-front: the user is choosing which one stays open.
        val reordered = listOf(threeDebts[2], threeDebts[1], threeDebts[0])
        val split = DebtAllocationCalculator.allocate(reordered, BigDecimal("400.00"))

        assertThat(split.map { it.debtId }).containsExactly("d-3", "d-2", "d-1").inOrder()
        assertThat(split.map { it.amount.toPlainString() })
            .containsExactly("150.00", "200.00", "50.00").inOrder()
    }

    @Test
    fun debtsPastWhereTheLumpRunsOut_getNoPaymentAtAll() {
        val split = DebtAllocationCalculator.allocate(threeDebts, BigDecimal("300.00"))

        // d-1 is cleared exactly; d-2 and d-3 must not pick up zero-amount payment rows.
        assertThat(split.map { it.debtId }).containsExactly("d-1")
    }

    @Test
    fun exactlyTheCeiling_clearsEveryDebt() {
        val split = DebtAllocationCalculator.allocate(threeDebts, BigDecimal("650.00"))

        assertThat(split.map { it.amount.toPlainString() })
            .containsExactly("300.00", "200.00", "150.00").inOrder()
    }

    @Test
    fun aSplitAlwaysSumsBackToTheLump() {
        val lump = BigDecimal("487.35")
        val split = DebtAllocationCalculator.allocate(threeDebts, lump)

        val total = split.fold(BigDecimal.ZERO) { sum, it -> sum + it.amount }
        assertThat(total.compareTo(lump)).isEqualTo(0)
    }

    @Test
    fun overTheCeiling_isBlocked() {
        val error = runCatching {
            DebtAllocationCalculator.allocate(threeDebts, BigDecimal("650.01"))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun nonPositiveLump_isBlocked() {
        val error = runCatching {
            DebtAllocationCalculator.allocate(threeDebts, BigDecimal.ZERO)
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun scaleDifferencesDoNotDefeatTheCeilingCheck() {
        // "650" and "650.00" are unequal to BigDecimal.equals but equal by value — the ceiling
        // check must compare by value or an exact-to-the-ceiling payment would be rejected.
        val split = DebtAllocationCalculator.allocate(threeDebts, BigDecimal("650"))

        assertThat(split).hasSize(3)
    }
}
