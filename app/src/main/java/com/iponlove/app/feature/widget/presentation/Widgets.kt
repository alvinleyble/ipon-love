package com.iponlove.app.feature.widget.presentation

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll

/**
 * Single entry point every balance-changing write / session change calls to refresh the home-screen
 * widgets (grill 2026-07-14, refresh level A). Resets the balance widget's soft-reveal to its
 * default first, so a peek never survives a data refresh, then repaints both widgets from the
 * committed state. Callers must invoke this *after* their Room write commits.
 */
object Widgets {
    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(BalanceWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs[BalanceWidget.USER_TOGGLED_KEY] = false
            }
        }
        AddTransactionWidget().updateAll(context)
        BalanceWidget().updateAll(context)
    }
}
