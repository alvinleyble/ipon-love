package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.repository.NotificationPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Whether the opt-in off-app sweep should be armed at all (ADR-0056 decisions 7-8) — the **single**
 * rule, so the toggle sites and the login self-heal can never disagree about it.
 *
 * It takes *both* preferences because the off-app toggle is a sub-row of the "Recurring reminders"
 * master: turning the master off greys the sub-row and preserves its stored value, but the schedule
 * itself must go, or the phone keeps waking every six hours only for the worker to find reminders
 * disabled and return — the existing-and-suppressed state decision 7 exists to avoid. Re-enabling
 * the master re-arms it from the preserved value.
 */
class ObserveRecurringSweepArmedUseCase @Inject constructor(
    private val repository: NotificationPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = combine(
        repository.observeRecurringRemindersEnabled(),
        repository.observeOffAppRecurringRemindersEnabled(),
    ) { masterEnabled, offAppEnabled -> masterEnabled && offAppEnabled }
}
