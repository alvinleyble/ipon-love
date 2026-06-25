package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import javax.inject.Inject

/**
 * Record a repayment against a debt; rejects a non-positive amount. Overpayment is not an
 * error here (the calculator floors remaining at zero) — the UI guards against it for UX.
 */
class RecordDebtPaymentUseCase @Inject constructor(
    private val repository: PartnerDebtRepository,
) {
    suspend operator fun invoke(payment: DebtPayment) {
        require(payment.amount.signum() > 0) { "Payment amount must be greater than zero" }
        repository.upsertPayment(payment)
    }
}
