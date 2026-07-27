package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.core.sync.LocalTransactionRunner
import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Receiver leg of a transaction-linked settlement (ADR-0019 #14, generalised by ADR-0055). The
 * signed-in user is the lender who was repaid: this records their own INCOME leg into
 * [receiverAccountId] (flagged `is_settlement` so it moves their balance but stays out of
 * Analysis) and stamps it onto every payment the payor's expense backs, so the board knows the
 * receiver leg is done.
 *
 * The payor's lump may have been split across several debts, so the unit here is the payor
 * *transaction*, not one payment: one INCOME for the lump, stamped onto the whole
 * [payorTxnId] group at once (ADR-0055 #6). A single-debt settlement is just a group of one,
 * so there is no separate path. Both writes commit together for the same reason the payor
 * side does — one income row backs N stamps.
 *
 * Optional and idempotent: if the user never runs this, the payments stay payor-only, and a
 * payment that already carries a receiver leg is left untouched (first writer wins).
 */
class AddSettlementIncomeUseCase @Inject constructor(
    private val transactionRunner: LocalTransactionRunner,
    private val upsertTransaction: UpsertTransactionUseCase,
    private val repository: PartnerDebtRepository,
) {
    suspend operator fun invoke(
        payorTxnId: String,
        amount: BigDecimal,
        receiverAccountId: String,
        note: String?,
        date: Instant = Instant.now(),
        transactionId: String = UUID.randomUUID().toString(),
    ) {
        require(amount.signum() > 0) { "Settlement amount must be greater than zero" }

        transactionRunner.run {
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

            repository.stampReceiverTxn(payorTxnId, transactionId)
        }
    }
}
