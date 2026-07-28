package com.iponlove.app.feature.recurring.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.iponlove.app.MainActivity
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.session.userIdOrNull
import com.iponlove.app.feature.notifications.domain.model.AppNotification
import com.iponlove.app.feature.notifications.domain.model.NotificationCategory
import com.iponlove.app.feature.notifications.domain.usecase.GetRaisedNotificationIdsUseCase
import com.iponlove.app.feature.notifications.domain.usecase.RecordNotificationUseCase
import com.iponlove.app.feature.notifications.presentation.SystemNotificationPresenter
import com.iponlove.app.feature.recurring.data.RecurringReminderBacklogStore
import com.iponlove.app.feature.recurring.domain.usecase.CheckRecurringDueUseCase
import com.iponlove.app.feature.recurring.domain.usecase.ObservePendingConfirmationsUseCase
import com.iponlove.app.feature.recurring.domain.usecase.RecurringReminderCopy
import com.iponlove.app.feature.recurring.domain.usecase.RecurringReminderDeliveryWindow
import com.iponlove.app.feature.settings.domain.usecase.ObserveRecurringRemindersEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveRecurringSweepArmedUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Sibling to [com.iponlove.app.feature.budgets.worker.BudgetAlertWorker] (ADR-0052 decision 1):
 * fires recurring due-date reminders. Writes an inbox row first (ADR-0053, Way A); a genuinely new
 * row also raises the best-effort OS push. [RecurringReminderBacklogStore] keeps the feature's
 * first-ever run from dumping every currently-pending occurrence as a burst of reminders
 * (ADR-0052 decision 3).
 *
 * Runs from **two** enqueue paths (ADR-0056):
 *  - the original one-time [WORK_NAME], enqueued at app open and after a sync — always on;
 *  - an opt-in periodic [PERIODIC_WORK_NAME] sweep, which additionally honours the daytime
 *    [RecurringReminderDeliveryWindow]. The window is keyed off [KEY_PERIODIC] rather than checked
 *    unconditionally so it can never silence a 3am *app-open* reminder (decision 4).
 */
@HiltWorker
class RecurringReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val observePendingConfirmations: ObservePendingConfirmationsUseCase,
    private val checkRecurringDue: CheckRecurringDueUseCase,
    private val observeRecurringRemindersEnabled: ObserveRecurringRemindersEnabledUseCase,
    private val observeRecurringSweepArmed: ObserveRecurringSweepArmedUseCase,
    private val currentUserProvider: CurrentUserProvider,
    private val backlogStore: RecurringReminderBacklogStore,
    private val recordNotification: RecordNotificationUseCase,
    private val getRaisedNotificationIds: GetRaisedNotificationIdsUseCase,
    private val presenter: SystemNotificationPresenter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val isPeriodic = inputData.getBoolean(KEY_PERIODIC, false)

        // The one-time path is only ever enqueued from inside the authenticated composition, so it
        // never needed a session guard. A periodic schedule outlives the session — it survives in
        // WorkManager's own database — and everything below reaches Room through
        // CurrentUserProvider.userId(), which throws by design when signed out (ADR-0056 decision 8).
        if (currentUserProvider.userIdOrNull() == null) return Result.success()

        if (isPeriodic) {
            // Suppress the *whole* check, not just the push (decision 5): recordNotification()
            // returning `created` is the sole push trigger, so writing the row at 03:40 would leave
            // the 09:00 run finding the slot already filled — a silent inbox row and no nudge.
            if (!RecurringReminderDeliveryWindow.isWithinDeliveryWindow(
                    LocalTime.now(ZoneId.systemDefault()),
                )
            ) {
                return Result.success()
            }
            // Defence in depth for the leak this item's own booking called out: a periodic request
            // outlives the app, the process and the session, so a missed cancel would wake the phone
            // every six hours forever. The four lifecycle sites are the real fix; this one makes an
            // orphaned schedule silent *and* self-healing rather than a stream of surprise pushes.
            // Same combined rule the arm sites use, so the worker can never disagree with them.
            if (!observeRecurringSweepArmed().first()) {
                cancelPeriodic(applicationContext)
                return Result.success()
            }
        }

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

        /**
         * Deliberately **distinct** from [WORK_NAME] (ADR-0056 decision 8): the one-time enqueue
         * uses `ExistingWorkPolicy.REPLACE` on every app open, so sharing a name would destroy the
         * periodic schedule the first time the app was opened.
         */
        const val PERIODIC_WORK_NAME = "ipon_recurring_reminders_periodic"

        /** Set on the periodic enqueue only — gates [RecurringReminderDeliveryWindow]. */
        private const val KEY_PERIODIC = "periodic"

        /**
         * Six hours, not twenty-four (ADR-0056 decision 2). WorkManager picks the moment within
         * each period; at 24h, one run landing overnight is suppressed by the delivery window and
         * the next attempt falls at a similar hour a day later — reminders could stay suppressed
         * for days while the schedule looked healthy. At 6h the runs are 6h apart regardless of
         * phase, so at least two always land inside the 13-hour window.
         */
        private const val PERIOD_HOURS = 6L

        fun buildRequest() = OneTimeWorkRequestBuilder<RecurringReminderWorker>().build()

        /**
         * No flex interval (it would narrow when the OS may run us, fighting our own daytime gate),
         * no `CONNECTED` constraint (the check is pure local Room — it works in airplane mode) and
         * no `BatteryNotLow` constraint (silently suppressing a Room read is the invisible
         * unreliability this feature exists to reduce).
         */
        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<RecurringReminderWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .setInputData(workDataOf(KEY_PERIODIC to true))
                .build()

        /**
         * Bring the periodic schedule in line with the stored preference. Idempotent, so it doubles
         * as the login self-heal: `KEEP` means an already-armed schedule keeps its existing timer
         * instead of restarting it on every launch, and a stale schedule left behind by a missed
         * cancel is torn down the next time the user signs in.
         */
        fun setPeriodicEnabled(context: Context, enabled: Boolean) {
            if (enabled) {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    buildPeriodicRequest(),
                )
            } else {
                cancelPeriodic(context)
            }
        }

        /** Sign-out / delete-account teardown — without this a signed-out phone wakes forever. */
        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
