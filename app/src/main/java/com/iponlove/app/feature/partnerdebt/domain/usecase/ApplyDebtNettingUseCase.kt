package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import javax.inject.Inject

/**
 * Reads the current couple snapshot and applies FIFO netting payments for [newDebt] against
 * any opposing open debts (ADR-0019 #9). Call this immediately after persisting [newDebt].
 *
 * The netting payments have deterministic ids, so running this on both devices after syncing
 * converges to the same rows rather than double-netting.
 */
class ApplyDebtNettingUseCase @Inject constructor(
    private val repository: PartnerDebtRepository,
    private val clock: SyncClock,
) {
    suspend operator fun invoke(newDebt: PartnerDebt, coupleId: String) {
        val allDebts = repository.getActiveDebts(coupleId)
        val allPayments = repository.getActivePayments()
        val now = clock.stamp(null)

        val result = DebtNettingCalculator.computeNetting(
            newDebt = newDebt,
            existingDebts = allDebts.filter { it.id != newDebt.id },
            existingPayments = allPayments,
            now = now,
        )

        result.payments.forEach { repository.upsertPayment(it) }
    }
}
