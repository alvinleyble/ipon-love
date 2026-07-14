package com.iponlove.app.feature.widget.presentation

/**
 * Repaints the home-screen widgets after a balance-changing / session change. Abstracted so non-UI
 * callers (e.g. [com.iponlove.app.feature.applock.presentation.AppLockManager]) don't depend on
 * Glance directly and stay unit-testable — production binds [GlanceWidgetRefresher].
 */
fun interface WidgetRefresher {
    suspend fun refresh()
}
