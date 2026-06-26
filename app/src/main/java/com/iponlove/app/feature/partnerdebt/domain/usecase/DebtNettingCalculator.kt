package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.core.util.DeterministicUuid
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import java.math.BigDecimal
import java.time.Instant

/**
 * Pure FIFO debt-netting logic (ADR-0019 #9).
 *
 * When a new debt is recorded in the opposite direction to one or more existing open debts,
 * [computeNetting] produces a paired list of [DebtPayment]s that:
 *  - settle (or reduce) the new debt by its opposing amount, oldest-first
 *  - reduce each opposing debt by the same offset
 *  - reference each other via [DebtPayment.counterDebtId]
 *
 * **Deterministic IDs** — each netting payment's id is `DeterministicUuid.v5("netting:$debtId:$counterDebtId")`.
 * Two partners creating opposing debts offline converge on the same pair of payment ids
 * because both sides see both debt ids after sync; the upsert collapses duplicates.
 *
 * Nothing is destroyed: netting payments are ordinary soft-deletable rows. Deleting a
 * debt removes its netting payments via the repository cascade (ADR-0010).
 */
object DebtNettingCalculator {

    data class NettingResult(val payments: List<DebtPayment>)

    /**
     * @param newDebt      The debt just created — the one that may be offset.
     * @param existingDebts All active debts for the couple **excluding** [newDebt].
     * @param existingPayments All active payments for the couple (pre-existing manual + netting).
     * @param now          Timestamp stamped on each generated payment.
     */
    fun computeNetting(
        newDebt: PartnerDebt,
        existingDebts: List<PartnerDebt>,
        existingPayments: List<DebtPayment>,
        now: Instant,
    ): NettingResult {
        // Opposing = same pair of people, but directions swapped.
        val opposing = existingDebts
            .filter { it.borrowerId == newDebt.lenderId && it.lenderId == newDebt.borrowerId }
            .sortedBy { it.createdAt }   // FIFO: oldest offset first

        if (opposing.isEmpty()) return NettingResult(emptyList())

        val paymentsByDebt = existingPayments.groupBy { it.debtId }

        val result = mutableListOf<DebtPayment>()
        var remainingNew = newDebt.amount

        for (existing in opposing) {
            if (remainingNew.signum() <= 0) break

            val paid = paymentsByDebt[existing.id].orEmpty()
                .fold(BigDecimal.ZERO) { acc, p -> acc + p.amount }
            val remainingExisting = (existing.amount - paid).max(BigDecimal.ZERO)

            if (remainingExisting.signum() <= 0) continue

            val offset = remainingNew.min(remainingExisting)

            // Payment ON newDebt, referencing existing as the counter.
            result += DebtPayment(
                id = DeterministicUuid.v5("netting:${newDebt.id}:${existing.id}").toString(),
                debtId = newDebt.id,
                amount = offset,
                note = null,
                date = now,
                isNetting = true,
                counterDebtId = existing.id,
            )

            // Payment ON existingDebt, referencing newDebt as the counter.
            result += DebtPayment(
                id = DeterministicUuid.v5("netting:${existing.id}:${newDebt.id}").toString(),
                debtId = existing.id,
                amount = offset,
                note = null,
                date = now,
                isNetting = true,
                counterDebtId = newDebt.id,
            )

            remainingNew -= offset
        }

        return NettingResult(result)
    }
}
