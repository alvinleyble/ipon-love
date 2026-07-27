package com.iponlove.app.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iponlove.app.feature.budgets.worker.BudgetAlertWorker
import com.iponlove.app.feature.partnerdebt.worker.PartnerDebtNotificationWorker
import com.iponlove.app.feature.recurring.worker.RecurringReminderWorker
import com.iponlove.app.feature.widget.presentation.Widgets
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background sync worker (ADR-0012). Calls [SyncEngine.sync] once when the device has
 * a network connection. Used for background retry and reconnect sync; in-process sync
 * (foreground, pull-to-refresh) calls [SyncEngine.sync] directly without WorkManager.
 *
 * Retries up to 3 times with 30-second exponential back-off on failure.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        syncEngine.sync()
        // A background pull may have changed balances — repaint the home-screen widgets (the
        // foreground post-sync path in MainActivity covers app-open; this covers WorkManager).
        Widgets.updateAll(applicationContext)
        enqueueBudgetAlerts()
        enqueueRecurringReminders()
        enqueuePartnerDebtAlerts()
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    private fun enqueueBudgetAlerts() {
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            BudgetAlertWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            BudgetAlertWorker.buildRequest(),
        )
    }

    private fun enqueueRecurringReminders() {
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            RecurringReminderWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            RecurringReminderWorker.buildRequest(),
        )
    }

    private fun enqueuePartnerDebtAlerts() {
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            PartnerDebtNotificationWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            PartnerDebtNotificationWorker.buildRequest(),
        )
    }

    companion object {
        const val WORK_NAME = "ipon_background_sync"
        private const val MAX_ATTEMPTS = 3

        /**
         * @param expedited run as expedited work — used by the widget's manual ⟳ (Item 36) so the
         * sync isn't deferred by the ROM's background throttling. Falls back to a normal request if
         * the app is out of its expedited quota. The background/login enqueues stay non-expedited.
         */
        fun buildRequest(expedited: Boolean = false): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .apply {
                    if (expedited) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
                .build()
    }
}
