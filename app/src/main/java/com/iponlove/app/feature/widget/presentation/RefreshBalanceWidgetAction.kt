package com.iponlove.app.feature.widget.presentation

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

/**
 * Manual refresh (the refresh icon) — a local re-render only: re-runs [BalanceWidget.provideGlance]
 * so it re-reads the latest committed Room snapshot and repaints, no network. A safety valve if a
 * write path ever misses [Widgets.updateAll]. Deliberately does *not* reset the soft reveal (unlike
 * a data-change refresh), so a manual refresh won't yank the amount the user is looking at.
 */
class RefreshBalanceWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        BalanceWidget().updateAll(context)
    }
}
