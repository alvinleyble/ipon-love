package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import javax.inject.Inject

/**
 * Create or edit a partner debt under [coupleId]: rejects a non-positive amount and a debt
 * a partner owes themselves (borrower must differ from lender).
 */
class UpsertPartnerDebtUseCase @Inject constructor(
    private val repository: PartnerDebtRepository,
) {
    suspend operator fun invoke(debt: PartnerDebt, coupleId: String) {
        require(debt.amount.signum() > 0) { "Debt amount must be greater than zero" }
        require(debt.borrowerId != debt.lenderId) { "A debt needs two different people" }
        repository.upsertDebt(debt, coupleId)
    }
}
