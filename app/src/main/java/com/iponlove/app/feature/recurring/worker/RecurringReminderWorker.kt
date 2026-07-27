package com.iponlove.app.feature.recurring.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.iponlove.app.MainActivity
import com.iponlove.app.feature.notifications.domain.model.AppNotification
import com.iponlove.app.feature.notifications.domain.model.NotificationCategory
import com.iponlove.app.feature.notifications.domain.usecase.GetRaisedNotificationIdsUseCase
import com.iponlove.app.feature.notifications.domain.usecase.RecordNotificationUseCase
import com.iponlove.app.feature.notifications.presentation.SystemNotificationPresenter
import com.iponlove.app.feature.recurring.data.RecurringReminderBacklogStore
import com.iponlove.app.feature.recurring.domain.usecase.CheckRecurringDueUseCase
import com.iponlove.app.feature.recurring.domain.usecase.ObservePendingConfirmationsUseCase
import com.iponlove.app.feature.recurring.domain.usecase.RecurringReminderCopy
import com.iponlove.app.feature.settings.domain.usecase.ObserveRecurringRemindersEnabledUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sibling to [com.iponlove.app.feature.budgets.worker.BudgetAlertWorker] (ADR-0052 decision 1):
 * fires recurring due-date reminders on app-open/sync, never on a scheduled clock alarm. Writes
 * an inbox row first (ADR-0053, Way A); a genuinely new row also raises the best-effort OS push.
 * [RecurringReminderBacklogStore] keeps the feature's first-ever run from dumping every
 * currently-pending occurrence as a burst of reminders (decision 3).
 */
@HiltWorker
class RecurringReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val observePendingConfirmations: ObservePendingConfirmationsUseCase,
    private val checkRecurringDue: CheckRecurringDueUseCase,
    private val observeRecurringRemindersEnabled: ObserveRecurringRemindersEnabledUseCase,
    private val backlogStore: RecurringReminderBacklogStore,
    private val recordNotification: RecordNotificationUseCase,
    private val getRaisedNotificationIds: GetRaisedNotificationIdsUseCase,
    private val presenter: SystemNotificationPresenter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!observeRecurringRemindersEnabled().first()) return Result.success()

        val today = LocalDate.now(ZoneId.systemDefault())
        val pending = observePendingConfirmations().first()

        val alreadyRaised = getRaisedNotificationIds(CheckRecurringDueUseCase.ID_PREFIX)
        // The backlog store freezes raw occurrence ids (it has no reason to know the inbox's id
        // shape); re-prefix before merging or every backlog id silently fails to match the
        // "recurring:{occurrenceId}" ids checkRecurringDue actually compares against, making the
        // whole guard a no-op (caught on-device: the very first pending occurrence fired instead
        // of being suppressed).
        val backlog = backlogStore.freeze(pending.map { it.occurrenceId }.toSet())
            .map(CheckRecurringDueUseCase::notificationId)
            .toSet()
        val due = checkRecurringDue(pending, alreadyRaised + backlog, today)

        val now = Instant.now()
        for (result in due) {
            val title = RecurringReminderCopy.title(result.pending.categoryName, result.pending.type)
            val created = recordNotification(
                id = result.notificationId,
                category = NotificationCategory.RECURRING,
                title = title,
                body = RecurringReminderCopy.BODY,
                deepLink = MainActivity.ROUTE_RECORDS,
            )
            // Push only on a genuinely new row — mirrors BudgetAlertWorker's guard against
            // re-detection on another device/sync raising the same occurrence first.
            if (created) {
                presenter.post(
                    AppNotification(
                        id = result.notificationId,
                        category = NotificationCategory.RECURRING,
                        title = title,
                        body = RecurringReminderCopy.BODY,
                        deepLink = MainActivity.ROUTE_RECORDS,
                        createdAt = now,
                        isRead = false,
                    ),
                )
            }
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "ipon_recurring_reminders"

        fun buildRequest() = OneTimeWorkRequestBuilder<RecurringReminderWorker>().build()
    }
}
