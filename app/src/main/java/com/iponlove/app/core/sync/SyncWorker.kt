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
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iponlove.app.feature.budgets.worker.BudgetAlertWorker
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
        enqueueBudgetAlerts()
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

    companion object {
        const val WORK_NAME = "ipon_background_sync"
        private const val MAX_ATTEMPTS = 3

        fun buildRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
    }
}
