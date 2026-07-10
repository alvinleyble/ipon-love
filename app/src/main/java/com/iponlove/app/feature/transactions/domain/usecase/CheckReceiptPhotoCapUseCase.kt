package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.PlanLimits
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import javax.inject.Inject

/**
 * The receipt-photos media cap gate (S8). A per-transaction media count (§10.1 `maxReceiptPhotos`,
 * INDIVIDUAL scope — free = 1 photo, premium = 3), consulted the moment a new receipt is picked.
 *
 * Unlike the S7 entity-count gates this owns no DAO query: the count is the draft editor's in-flight
 * image count — transient per-editor UI state the ViewModel already holds, not a persisted row set —
 * so [currentCount] is passed in rather than read here (the gate's contract, G6, puts the count on
 * the caller). The hard [com.iponlove.app.feature.transactions.domain.model.TransactionImage.MAX]
 * ceiling (= the premium max) still backstops the add path for dormant/premium users; this gate only
 * limits the free tier.
 */
class CheckReceiptPhotoCapUseCase @Inject constructor(
    private val gate: PremiumGate,
) {
    suspend operator fun invoke(currentCount: Int): CapCheck =
        gate.checkCap(Scope.INDIVIDUAL, currentCount, PlanLimits::maxReceiptPhotos)
}
