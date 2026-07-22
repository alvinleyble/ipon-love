package com.iponlove.app.feature.widget.presentation

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.session.userIdOrNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Self-heal for the balance widget's `NotReady` ("Updating…") state (Item 10).
 *
 * [BalanceWidget.provideGlance] renders `NotReady` when the session hint says logged-in but the live
 * Supabase session hasn't resolved to Authenticated yet — a cold process the widget host revived to
 * draw itself (MainActivity never ran), or a slow token refresh. [WidgetSessionHintWriter] repaints
 * on the *first* Authenticated transition (Fix A), but that `distinctUntilChanged`-gated signal can't
 * fire when a warm, already-authenticated process merely re-binds the widget (resize / update) during
 * a transient not-ready blip — leaving "Updating…" stuck until an app-open, a write, ⟳, or the 30-min
 * timer. This worker closes that gap by actively polling: provideGlance schedules it on a NotReady
 * render, and each run re-checks the live session — repainting real data the moment it's ready, then
 * stopping. Capped so a genuinely-offline/never-restoring session gives up gracefully (⟳ / opening the
 * app stay as manual escape hatches) rather than polling forever.
 */
@HiltWorker
class BalanceWidgetRetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val currentUser: CurrentUserProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (currentUser.userIdOrNull() != null) {
            // Session resolved — repaint (provideGlance now reads real Room data) and stop the chain.
            BalanceWidget().updateAll(applicationContext)
            return Result.success()
        }
        // Still not ready: back off and try again until the cap, then give up.
        val attempt = inputData.getInt(KEY_ATTEMPT, 0)
        if (attempt + 1 < MAX_ATTEMPTS) {
            schedule(applicationContext, attempt + 1, replaceExisting = true)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "balance_widget_session_retry"
        private const val KEY_ATTEMPT = "attempt"
        private const val MAX_ATTEMPTS = 6

        /**
         * Schedule a re-check with capped exponential backoff (2, 4, 8, 16, 30, 30s).
         *
         * @param replaceExisting the worker chaining its own next step passes `true` (continue the
         * chain); a fresh NotReady render from [BalanceWidget.provideGlance] passes `false` (KEEP), so
         * repeated renders never restart or stack a chain that's already in flight.
         */
        fun schedule(context: Context, attempt: Int = 0, replaceExisting: Boolean = false) {
            val delaySeconds = minOf(2L shl attempt, 30L)
            val request = OneTimeWorkRequestBuilder<BalanceWidgetRetryWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_ATTEMPT to attempt))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
