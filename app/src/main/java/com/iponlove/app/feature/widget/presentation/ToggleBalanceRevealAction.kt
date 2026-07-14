package com.iponlove.app.feature.widget.presentation

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll

/**
 * Flips the balance widget's in-place *soft* reveal (the eye button). Local and ephemeral: it only
 * touches this widget instance's Glance state, never the global "Hide amounts" pref, and any data
 * refresh (via [Widgets.updateAll]) resets it — so a peek never survives on the home screen.
 */
class ToggleBalanceRevealAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[BalanceWidget.USER_TOGGLED_KEY] = !(prefs[BalanceWidget.USER_TOGGLED_KEY] ?: false)
        }
        // Re-render from the just-written state. Each instance reads its own reveal flag, so
        // updateAll repaints the toggled widget correctly (single-instance update() isn't exposed).
        BalanceWidget().updateAll(context)
    }
}
