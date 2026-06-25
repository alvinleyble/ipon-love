package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import javax.inject.Inject

/** Soft-delete a debt (ADR-0010). Its payments stay in the table but are ignored once the
 *  debt is gone — the calculator only sums payments of active debts. */
class DeletePartnerDebtUseCase @Inject constructor(
    private val repository: PartnerDebtRepository,
) {
    suspend operator fun invoke(id: String) = repository.deleteDebt(id)
}
