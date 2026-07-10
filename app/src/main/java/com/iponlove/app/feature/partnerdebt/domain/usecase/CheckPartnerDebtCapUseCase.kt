package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.PlanLimits
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import javax.inject.Inject

/**
 * The couple-debt count-cap gate (S7). The couple-debt entry is a SHARED entity (either partner's
 * premium unlocks it, D1). Per G1 only **un-settled** debts count — settled ones don't consume a
 * slot — so the caller passes the already-derived un-settled count from the debt board (the board
 * is the single source of truth for `isSettled`, computed from payments; re-aggregating it here
 * would risk drift), and this only applies the tier/enforcement decision.
 */
class CheckPartnerDebtCapUseCase @Inject constructor(
    private val gate: PremiumGate,
) {
    suspend operator fun invoke(currentUnsettledCount: Int): CapCheck =
        gate.checkCap(Scope.SHARED, currentUnsettledCount, PlanLimits::maxCoupleDebtEntries)
}
