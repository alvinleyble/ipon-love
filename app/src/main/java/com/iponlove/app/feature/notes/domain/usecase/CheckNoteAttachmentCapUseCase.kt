package com.iponlove.app.feature.notes.domain.usecase

import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.PlanLimits
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import javax.inject.Inject

/**
 * The note-attachments media cap gate (S8). A per-note media count (§10.1 `maxNoteAttachments`,
 * INDIVIDUAL scope — free = 0, so the *first* attachment is blocked when enforced; premium = 3),
 * consulted the moment a new image is picked.
 *
 * Like [CheckReceiptPhotoCapUseCase] it owns no DAO query: the count is the editor's live attachment
 * list size — per-note UI state the ViewModel already holds — so [currentCount] is passed in (G6
 * puts the count on the caller). The hard `MAX_ATTACHMENTS` ceiling in the editor (= the premium
 * max) still backstops the add path for dormant/premium users; this gate only limits the free tier.
 */
class CheckNoteAttachmentCapUseCase @Inject constructor(
    private val gate: PremiumGate,
) {
    suspend operator fun invoke(currentCount: Int): CapCheck =
        gate.checkCap(Scope.INDIVIDUAL, currentCount, PlanLimits::maxNoteAttachments)
}
