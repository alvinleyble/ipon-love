package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.recurring.domain.model.PendingConfirmation
import java.time.LocalDate
import javax.inject.Inject

/** One due-but-unconfirmed occurrence that should raise a due-date reminder now. */
data class RecurringReminderResult(
    val pending: PendingConfirmation,
    val notificationId: String,
)

/**
 * Pure domain use case (ADR-0052). Given the live "To confirm" list, returns the occurrences
 * that are due — on or past their date, never before (no advance warning, decision 3) — and
 * have not already been raised. [alreadyRaisedIds] is the caller's union of the inbox's own
 * dedup (ADR-0053, "already notified") and the one-time backlog freeze (decision 3, "existed
 * before reminders could ever have fired for it") — this use case only knows about "due" vs
 * "already accounted for".
 */
class CheckRecurringDueUseCase @Inject constructor() {

    operator fun invoke(
        pending: List<PendingConfirmation>,
        alreadyRaisedIds: Set<String>,
        today: LocalDate,
    ): List<RecurringReminderResult> =
        pending
            .asSequence()
            .filter { !it.date.isAfter(today) }
            .mapNotNull { candidate ->
                val id = notificationId(candidate.occurrenceId)
                if (id in alreadyRaisedIds) null else RecurringReminderResult(candidate, id)
            }
            .toList()

    companion object {
        /** Prefix every recurring reminder id shares — the inbox query filter for this category. */
        const val ID_PREFIX = "recurring:"

        /** Deterministic inbox id — [PendingConfirmation.occurrenceId] is already stable across
         *  devices, so phone and web raising the same occurrence merge (ADR-0053). */
        fun notificationId(occurrenceId: String) = "$ID_PREFIX$occurrenceId"
    }
}
