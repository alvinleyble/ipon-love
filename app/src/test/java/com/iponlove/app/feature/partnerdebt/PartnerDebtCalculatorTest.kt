package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.partnerdebt.domain.model.NetDirection
import com.iponlove.app.feature.partnerdebt.domain.usecase.PartnerDebtCalculator
import com.iponlove.app.feature.user.domain.model.User
import java.math.BigDecimal
import java.time.Instant
import org.junit.Test

/**
 * The partner-debt money math: per-debt remaining = amount − Σ(payments) floored at zero,
 * and the couple's net signed by who borrowed. Pure aggregation — exhaustively covered.
 */
class PartnerDebtCalculatorTest {

    private val me = User(id = "me", displayName = "Alvin", accentColor = "#FF0000", coupleId = "c-1")
    private val partner = User(id = "you", displayName = "Patty", accentColor = "#00FF00", coupleId = "c-1")

    @Test
    fun debtIBorrowed_countsAsIOwe_withRemainingAfterPayments() {
        val debts = listOf(partnerDebt("d", borrowerId = "me", lenderId = "you", amount = "1000.00"))
        val payments = listOf(debtPayment("p", debtId = "d", amount = "300.00"))

        val board = PartnerDebtCalculator.summarize(me, partner, debts, payments)

        val item = board.debts.single()
        assertThat(item.iAmBorrower).isTrue()
        assertThat(item.paid).isEqualTo(BigDecimal("300.00"))
        assertThat(item.remaining).isEqualTo(BigDecimal("700.00"))
        assertThat(item.isSettled).isFalse()
        assertThat(item.counterpartName).isEqualTo("Patty")
        assertThat(board.net.direction).isEqualTo(NetDirection.I_OWE)
        assertThat(board.net.amount).isEqualTo(BigDecimal("700.00"))
        assertThat(board.net.counterpartName).isEqualTo("Patty")
    }

    @Test
    fun debtPartnerBorrowed_countsAsOwedToMe() {
        val debts = listOf(partnerDebt("d", borrowerId = "you", lenderId = "me", amount = "500.00"))

        val board = PartnerDebtCalculator.summarize(me, partner, debts, payments = emptyList())

        assertThat(board.debts.single().iAmBorrower).isFalse()
        assertThat(board.net.direction).isEqualTo(NetDirection.OWED_TO_ME)
        assertThat(board.net.amount).isEqualTo(BigDecimal("500.00"))
    }

    @Test
    fun nettingOpposingDebts_subtractsAcrossDirections() {
        val debts = listOf(
            partnerDebt("owe", borrowerId = "me", lenderId = "you", amount = "800.00"),
            partnerDebt("owed", borrowerId = "you", lenderId = "me", amount = "300.00"),
        )

        val board = PartnerDebtCalculator.summarize(me, partner, debts, payments = emptyList())

        // 800 I owe − 300 owed to me = 500 net, I owe.
        assertThat(board.net.direction).isEqualTo(NetDirection.I_OWE)
        assertThat(board.net.amount).isEqualTo(BigDecimal("500.00"))
    }

    @Test
    fun fullyPaidDebt_isSettled_andNetsToZero() {
        val debts = listOf(partnerDebt("d", borrowerId = "me", lenderId = "you", amount = "1000.00"))
        val payments = listOf(
            debtPayment("p1", debtId = "d", amount = "600.00"),
            debtPayment("p2", debtId = "d", amount = "400.00"),
        )

        val board = PartnerDebtCalculator.summarize(me, partner, debts, payments)

        val item = board.debts.single()
        assertThat(item.remaining).isEqualTo(BigDecimal("0.00"))
        assertThat(item.isSettled).isTrue()
        assertThat(item.fraction).isEqualTo(1f)
        assertThat(board.net.direction).isEqualTo(NetDirection.SETTLED)
        assertThat(board.net.amount.signum()).isEqualTo(0)
    }

    @Test
    fun overpayment_floorsRemainingAtZero_andClampsFraction() {
        val debts = listOf(partnerDebt("d", borrowerId = "me", lenderId = "you", amount = "100.00"))
        val payments = listOf(debtPayment("p", debtId = "d", amount = "250.00"))

        val item = PartnerDebtCalculator.summarize(me, partner, debts, payments).debts.single()

        assertThat(item.paid).isEqualTo(BigDecimal("250.00"))
        assertThat(item.remaining.signum()).isEqualTo(0)
        assertThat(item.fraction).isEqualTo(1f)
        assertThat(item.isSettled).isTrue()
    }

    @Test
    fun paymentsAreGroupedPerDebt_andOrphansIgnored() {
        val debts = listOf(
            partnerDebt("d1", borrowerId = "me", lenderId = "you", amount = "1000.00"),
            partnerDebt("d2", borrowerId = "me", lenderId = "you", amount = "500.00"),
        )
        val payments = listOf(
            debtPayment("p1", debtId = "d1", amount = "200.00"),
            debtPayment("p2", debtId = "d2", amount = "100.00"),
            debtPayment("orphan", debtId = "gone", amount = "999.00"), // no matching debt
        )

        val board = PartnerDebtCalculator.summarize(me, partner, debts, payments)

        val byId = board.debts.associateBy { it.id }
        assertThat(byId.getValue("d1").remaining).isEqualTo(BigDecimal("800.00"))
        assertThat(byId.getValue("d2").remaining).isEqualTo(BigDecimal("400.00"))
        // Net = 800 + 400 = 1200 (orphan payment never reduces anything).
        assertThat(board.net.amount).isEqualTo(BigDecimal("1200.00"))
    }

    @Test
    fun unsettledDebtsSortFirst_thenNewestWithinGroup() {
        val debts = listOf(
            partnerDebt("oldOpen", amount = "100.00", createdAt = Instant.ofEpochMilli(1_000)),
            partnerDebt("settled", amount = "100.00", createdAt = Instant.ofEpochMilli(5_000)),
            partnerDebt("newOpen", amount = "100.00", createdAt = Instant.ofEpochMilli(3_000)),
        )
        val payments = listOf(debtPayment("p", debtId = "settled", amount = "100.00"))

        val board = PartnerDebtCalculator.summarize(me, partner, debts, payments)

        // Open debts first (newest open before older open), settled last.
        assertThat(board.debts.map { it.id }).containsExactly("newOpen", "oldOpen", "settled").inOrder()
    }

    @Test
    fun partnerNameNull_untilPartnerRowSyncsIn() {
        val debts = listOf(partnerDebt("d", borrowerId = "me", lenderId = "you", amount = "100.00"))

        val board = PartnerDebtCalculator.summarize(me, partner = null, debts = debts, payments = emptyList())

        assertThat(board.net.counterpartName).isNull()
        assertThat(board.debts.single().counterpartName).isNull()
    }
}
