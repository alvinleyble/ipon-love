package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.LocalTransactionRunner
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtRepositoryImpl
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtSettlementDeletionEffects
import com.iponlove.app.feature.partnerdebt.domain.usecase.PartnerDebtCalculator
import com.iponlove.app.feature.transactions.FakeTransactionDao
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.iponlove.app.feature.transactions.transactionEntity
import com.iponlove.app.feature.user.domain.model.User
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * End-to-end coverage of the live bug (ADR-0065): deleting a settlement transaction used to
 * leave its `DebtPayment` group fully credited while the money it recorded was gone from the
 * ledger. Wired through the real seam — [DeleteTransactionUseCase] →
 * [com.iponlove.app.feature.transactions.domain.usecase.SettlementDeletionEffects] →
 * [PartnerDebtRepository] — not asserted at a single layer, so a wiring regression (e.g. the
 * Hilt binding pointing at the wrong impl) would show up here even though each layer's own
 * unit tests still pass in isolation.
 */
class SettlementRetirementTest {

    private val transactionDao = FakeTransactionDao()
    private val debtDao = FakePartnerDebtDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val transactionRepository = TransactionRepositoryImpl(
        dao = transactionDao,
        clock = clock,
        currentUser = CurrentUserProvider { "user-1" },
    )
    private val debtRepository = PartnerDebtRepositoryImpl(debtDao, clock)
    private val settlementEffects = PartnerDebtSettlementDeletionEffects(debtRepository)
    private val deleteTransaction = DeleteTransactionUseCase(
        transactionRepository,
        LocalTransactionRunner { block -> block() },
        settlementEffects,
    )

    private val me = User(id = "me", displayName = "Alvin", accentColor = null, coupleId = "c-1")

    @Test
    fun deletingSettlementExpense_retiresGroup_debtReadsOutstandingAgain() = runTest {
        val debt = partnerDebt("d-1", borrowerId = "me", lenderId = "you", amount = "500.00")
        debtDao.payments["p-1"] = debtPaymentEntity(
            id = "p-1", debtId = "d-1", amount = "500.00", payorTxnId = "txn-pay",
        )
        transactionDao.store["txn-pay"] = transactionEntity(id = "txn-pay", isSettlement = true, amount = "500.00")

        val before = PartnerDebtCalculator.summarize(me, null, listOf(debt), debtRepository.getActivePayments())
        assertThat(before.debts.single().isSettled).isTrue()

        deleteTransaction("txn-pay")

        assertThat(debtDao.payments.getValue("p-1").isDeleted).isTrue()
        val after = PartnerDebtCalculator.summarize(me, null, listOf(debt), debtRepository.getActivePayments())
        val item = after.debts.single()
        assertThat(item.paid).isEqualTo(BigDecimal.ZERO)
        assertThat(item.remaining.toPlainString()).isEqualTo("500.00")
        assertThat(item.isSettled).isFalse()
    }

    @Test
    fun deletingLumpSettlementExpense_retiresEveryPaymentInTheGroup() = runTest {
        val debts = listOf(
            partnerDebt("d-1", amount = "300.00"),
            partnerDebt("d-2", amount = "200.00"),
            partnerDebt("d-3", amount = "50.00"),
        )
        listOf("p-1" to "d-1", "p-2" to "d-2", "p-3" to "d-3").forEach { (paymentId, debtId) ->
            debtDao.payments[paymentId] = debtPaymentEntity(id = paymentId, debtId = debtId, payorTxnId = "txn-lump")
        }
        transactionDao.store["txn-lump"] = transactionEntity(id = "txn-lump", isSettlement = true, amount = "550.00")

        deleteTransaction("txn-lump")

        assertThat(debtDao.payments.values.all { it.isDeleted }).isTrue()
        val after = PartnerDebtCalculator.summarize(me, null, debts, debtRepository.getActivePayments())
        assertThat(after.debts.all { !it.isSettled }).isTrue()
    }

    @Test
    fun deletingSettlementIncome_clearsReceiverStamp_leavesAmountAndDebtUntouched() = runTest {
        debtDao.payments["p-1"] = debtPaymentEntity(
            id = "p-1", debtId = "d-1", amount = "500.00", payorTxnId = "txn-pay", receiverTxnId = "txn-recv",
        )
        transactionDao.store["txn-recv"] =
            transactionEntity(id = "txn-recv", type = TransactionType.INCOME, isSettlement = true, amount = "500.00")

        deleteTransaction("txn-recv")

        val payment = debtDao.payments.getValue("p-1")
        assertThat(payment.receiverTxnId).isNull()
        assertThat(payment.isDeleted).isFalse() // the payment itself is untouched, only the stamp
        assertThat(payment.amount.toPlainString()).isEqualTo("500.00")
        assertThat(payment.debtId).isEqualTo("d-1")
    }

    @Test
    fun deletingOrdinaryExpenseTransferFeeAndAdjustment_retiresNothing() = runTest {
        debtDao.payments["p-unrelated"] = debtPaymentEntity(id = "p-unrelated", debtId = "d-1", payorTxnId = "txn-other")
        transactionDao.store["t-expense"] = transactionEntity(id = "t-expense")
        transactionDao.store["fee-1"] = transactionEntity(id = "fee-1")
        transactionDao.store["transfer-1"] =
            transactionEntity(id = "transfer-1", type = TransactionType.TRANSFER, transferFeeTransactionId = "fee-1")
        transactionDao.store["t-adjustment"] = transactionEntity(id = "t-adjustment", isAdjustment = true)

        deleteTransaction("t-expense")
        deleteTransaction("transfer-1")
        deleteTransaction("t-adjustment")

        assertThat(debtDao.payments.getValue("p-unrelated").isDeleted).isFalse()
        assertThat(transactionDao.store.getValue("fee-1").isDeleted).isTrue() // cascaded, but not a settlement
    }

    @Test
    fun deletingAnAlreadyDeletedSettlement_isIdempotent_doesNotCorruptState() = runTest {
        debtDao.payments["p-1"] = debtPaymentEntity(id = "p-1", debtId = "d-1", amount = "500.00", payorTxnId = "txn-pay")
        transactionDao.store["txn-pay"] = transactionEntity(id = "txn-pay", isSettlement = true, amount = "500.00")

        deleteTransaction("txn-pay")
        val afterFirst = debtDao.payments.getValue("p-1")
        now = Instant.ofEpochMilli(99_999)

        deleteTransaction("txn-pay")

        assertThat(debtDao.payments.getValue("p-1")).isEqualTo(afterFirst)
    }
}
