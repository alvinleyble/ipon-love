package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Receiver leg of a transaction-linked settlement (ADR-0019 #14). The signed-in user is the
 * lender who was repaid: this records their own INCOME leg into [receiverAccountId] (flagged
 * `is_settlement` so it moves their balance but stays out of Analysis) and stamps
 * [PartnerDebtRepository.stampReceiverTxn] onto the existing settlement payment so the board
 * knows the receiver leg is done.
 *
 * Optional and idempotent: the income is written first, then the stamp (first-writer-wins on
 * the couple-owned payment row). If the user never runs this, the payment stays payor-only.
 */
class AddSettlementIncomeUseCase @Inject constructor(
    private val upsertTransaction: UpsertTransactionUseCase,
    private val repository: PartnerDebtRepository,
) {
    suspend operator fun invoke(
        paymentId: String,
        amount: BigDecimal,
        receiverAccountId: String,
        note: String?,
        date: Instant = Instant.now(),
        transactionId: String = UUID.randomUUID().toString(),
    ) {
        require(amount.signum() > 0) { "Settlement amount must be greater than zero" }

        upsertTransaction(
            Transaction(
                id = transactionId,
                type = TransactionType.INCOME,
                amount = amount,
                accountId = receiverAccountId,
                categoryId = null,
                note = note,
                date = date,
                isSettlement = true,
            ),
        )

        repository.stampReceiverTxn(paymentId, transactionId)
    }
}
