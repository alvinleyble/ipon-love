package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

/**
 * "Paid for partner" entry (ADR-0019 #12): records the [transaction] normally **and**
 * auto-creates a partner debt for the part the partner owes ([amountOwed], ≤ the transaction
 * amount — full pay or a bill split). The lender ([lenderId]) is whoever made the payment;
 * the borrower ([borrowerId]) is the partner.
 *
 * The debt links back to the transaction via [PartnerDebt.sourceTransactionId] for display
 * only — **fire-and-forget**: later editing or deleting the transaction never touches the
 * debt. Creating the debt runs through [UpsertPartnerDebtUseCase], so it immediately feeds
 * the FIFO auto-netting against any opposing open debts (ADR-0019 #9).
 */
class PaidOnBehalfUseCase @Inject constructor(
    private val upsertTransaction: UpsertTransactionUseCase,
    private val upsertPartnerDebt: UpsertPartnerDebtUseCase,
) {
    suspend operator fun invoke(
        transaction: Transaction,
        amountOwed: BigDecimal,
        borrowerId: String,
        lenderId: String,
        coupleId: String,
        debtId: String = UUID.randomUUID().toString(),
    ) {
        require(amountOwed.signum() > 0) { "Amount owed must be greater than zero" }
        require(amountOwed <= transaction.amount) {
            "Amount owed cannot exceed the transaction amount"
        }

        // Record the real money movement first so the ledger reflects the spend even if the
        // debt write fails; the debt is the derived/secondary record.
        upsertTransaction(transaction)

        val debt = PartnerDebt(
            id = debtId,
            borrowerId = borrowerId,
            lenderId = lenderId,
            amount = amountOwed,
            description = transaction.note,
            createdAt = transaction.date,
            sourceTransactionId = transaction.id,
        )
        upsertPartnerDebt(debt, coupleId)
    }
}
