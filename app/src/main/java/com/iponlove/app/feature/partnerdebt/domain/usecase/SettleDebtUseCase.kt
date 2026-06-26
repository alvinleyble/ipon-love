package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Payor leg of a transaction-linked settlement (ADR-0019 #14). The signed-in user is the
 * borrower paying down [debtId]: this records their own EXPENSE leg from [payorAccountId]
 * (flagged `is_settlement` so it moves their balance but stays out of Analysis) **and** the
 * couple-owned [DebtPayment] that reduces the debt, linking the two via [payorTxnId].
 *
 * The transaction is written first (real money moved) so the ledger reflects the spend even
 * if the payment write fails. The receiver's matching income is a separate, optional leg
 * ([AddSettlementIncomeUseCase]) — payor-only is a valid, fully-settled state.
 */
class SettleDebtUseCase @Inject constructor(
    private val upsertTransaction: UpsertTransactionUseCase,
    private val recordPayment: RecordDebtPaymentUseCase,
) {
    suspend operator fun invoke(
        debtId: String,
        amount: BigDecimal,
        payorAccountId: String,
        note: String?,
        date: Instant = Instant.now(),
        transactionId: String = UUID.randomUUID().toString(),
        paymentId: String = UUID.randomUUID().toString(),
    ) {
        require(amount.signum() > 0) { "Settlement amount must be greater than zero" }

        upsertTransaction(
            Transaction(
                id = transactionId,
                type = TransactionType.EXPENSE,
                amount = amount,
                accountId = payorAccountId,
                categoryId = null,
                note = note,
                date = date,
                isSettlement = true,
            ),
        )

        recordPayment(
            DebtPayment(
                id = paymentId,
                debtId = debtId,
                amount = amount,
                note = note,
                date = date,
                payorAccountId = payorAccountId,
                payorTxnId = transactionId,
            ),
        )
    }
}
