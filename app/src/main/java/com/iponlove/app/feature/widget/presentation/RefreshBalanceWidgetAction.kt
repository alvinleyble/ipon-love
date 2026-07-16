package com.iponlove.app.feature.widget.presentation

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.iponlove.app.core.sync.SyncWorker
import dagger.hilt.android.EntryPointAccessors

/**
 * The refresh icon: repaint immediately from local Room **and** pull fresh remote data on demand
 * (Item 36 — the ⟳ used to be local-only, so a change made on another device or by a partner never
 * reached the widget without opening the app). Manual sync in the ADR-0002 spirit.
 *
 * 1. Re-run [BalanceWidget.provideGlance] right away so the tap feels responsive even offline, and
 *    so any newer *local* write shows at once. This deliberately does **not** reset the soft reveal
 *    (unlike a data-change [Widgets.updateAll]) — a manual refresh must not yank the amount the user
 *    is looking at.
 * 2. Then enqueue an **expedited** [SyncWorker] so the ROM's background throttling can't defer it;
 *    its existing sync → [Widgets.updateAll] → budget-alert chain repaints the converged figure when
 *    it lands (that repaint *does* reset the peek — accepted data-refresh semantics). Skipped when
 *    signed out (nothing to sync) — read from the same fast session hint the widget itself uses.
 */
class RefreshBalanceWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        BalanceWidget().updateAll(context)

        val store = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .widgetSessionStore()
        val signedIn = store.hasSession() == true
        if (signedIn) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                SyncWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                SyncWorker.buildRequest(expedited = true),
            )
        }
    }
}
