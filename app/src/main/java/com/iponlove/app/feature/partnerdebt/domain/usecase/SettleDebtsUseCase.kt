package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.core.sync.LocalTransactionRunner
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Payor leg of a transaction-linked settlement (ADR-0019 #14, generalised by ADR-0055). The
 * signed-in user is the borrower paying down one *or several* of their debts with a single
 * lump: this records **one** flagged EXPENSE for the whole lump (the money left the account
 * once) plus one couple-owned [DebtPayment] per [allocations] entry, all linked to that
 * expense through the shared [DebtPayment.payorTxnId].
 *
 * Unlike the rest of the debt feature's sequential, best-effort writes, this commits inside a
 * single [LocalTransactionRunner.run]: one expense backs N payments, so a partial write would
 * leave the ledger overstating what actually reached the debts (ADR-0055 #7). A future change
 * here must preserve the all-or-nothing guarantee.
 *
 * The receiver's matching income is a separate, optional leg ([AddSettlementIncomeUseCase]) —
 * payor-only is a valid, fully-settled state.
 */
class SettleDebtsUseCase @Inject constructor(
    private val transactionRunner: LocalTransactionRunner,
    private val upsertTransaction: UpsertTransactionUseCase,
    private val recordPayment: RecordDebtPaymentUseCase,
) {
    suspend operator fun invoke(
        allocations: List<DebtAllocation>,
        payorAccountId: String,
        note: String?,
        date: Instant = Instant.now(),
        transactionId: String = UUID.randomUUID().toString(),
        paymentIds: List<String> = allocations.map { UUID.randomUUID().toString() },
    ) {
        require(allocations.isNotEmpty()) { "A settlement must cover at least one debt" }
        require(paymentIds.size == allocations.size) { "Every allocation needs its own payment id" }
        val lump = allocations.fold(BigDecimal.ZERO) { sum, allocation -> sum + allocation.amount }
        require(lump.signum() > 0) { "Settlement amount must be greater than zero" }

        transactionRunner.run {
            upsertTransaction(
                Transaction(
                    id = transactionId,
                    type = TransactionType.EXPENSE,
                    amount = lump,
                    accountId = payorAccountId,
                    categoryId = null,
                    note = note,
                    date = date,
                    isSettlement = true,
                ),
            )

            allocations.forEachIndexed { index, allocation ->
                recordPayment(
                    DebtPayment(
                        id = paymentIds[index],
                        debtId = allocation.debtId,
                        amount = allocation.amount,
                        note = note,
                        date = date,
                        payorAccountId = payorAccountId,
                        payorTxnId = transactionId,
                    ),
                )
            }
        }
    }
}
