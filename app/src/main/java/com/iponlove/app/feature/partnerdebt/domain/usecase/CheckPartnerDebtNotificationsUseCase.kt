package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import javax.inject.Inject

/** A partner-authored debt that should raise a "partner logged a new debt" notification now. */
data class PartnerDebtNotificationResult(
    val debt: PartnerDebt,
    val notificationId: String,
)

/**
 * Pure domain use case (Item 9 grill). Notify the current user iff a debt was authored by their
 * partner **and** the current user is the borrower — "Patty logs Alvin owes me" notifies Alvin;
 * either direction of a self-authored debt never fires (filtered out via [seenDebtIds] before
 * this even sees the borrower check matter). [seenDebtIds] is the caller's union of locally
 * authored debt ids and the one-time backlog freeze — both are raw [PartnerDebt.id] values, kept
 * deliberately separate from [alreadyRaisedIds] (the inbox's own prefixed dedup) so the two id
 * shapes never need remapping across each other, the class of bug Item 1's backlog guard hit.
 */
class CheckPartnerDebtNotificationsUseCase @Inject constructor() {

    operator fun invoke(
        debts: List<PartnerDebt>,
        currentUserId: String,
        seenDebtIds: Set<String>,
        alreadyRaisedIds: Set<String>,
    ): List<PartnerDebtNotificationResult> =
        debts
            .asSequence()
            .filter { it.borrowerId == currentUserId }
            .filter { it.id !in seenDebtIds }
            .mapNotNull { debt ->
                val id = notificationId(debt.id)
                if (id in alreadyRaisedIds) null else PartnerDebtNotificationResult(debt, id)
            }
            .toList()

    companion object {
        /** Prefix every partner-debt alert id shares — the inbox query filter for this category. */
        const val ID_PREFIX = "debt:"

        /** Deterministic inbox id — [PartnerDebt.id] is already stable/synced, so phone and web
         *  raising the same debt merge (ADR-0053). */
        fun notificationId(debtId: String) = "$ID_PREFIX$debtId"
    }
}
