package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.feature.partnerdebt.data.PartnerDebtSeenStore
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import javax.inject.Inject

/**
 * Create or edit a partner debt under [coupleId]: rejects a non-positive amount and a debt
 * a partner owes themselves (borrower must differ from lender). After persisting, runs
 * [ApplyDebtNettingUseCase] to auto-offset opposing open debts (ADR-0019 #9).
 *
 * The sole choke point both debt-creation paths (manual add, "paid for partner") funnel through,
 * so it also marks [debt] as locally authored (Item 9 grill) — this device's own debts must
 * never raise their own author's "partner logged a new debt" notification.
 */
class UpsertPartnerDebtUseCase @Inject constructor(
    private val repository: PartnerDebtRepository,
    private val applyNetting: ApplyDebtNettingUseCase,
    private val seenStore: PartnerDebtSeenStore,
) {
    suspend operator fun invoke(debt: PartnerDebt, coupleId: String) {
        require(debt.amount.signum() > 0) { "Debt amount must be greater than zero" }
        require(debt.borrowerId != debt.lenderId) { "A debt needs two different people" }
        repository.upsertDebt(debt, coupleId)
        seenStore.markAuthored(debt.id)
        applyNetting(debt, coupleId)
    }
}
