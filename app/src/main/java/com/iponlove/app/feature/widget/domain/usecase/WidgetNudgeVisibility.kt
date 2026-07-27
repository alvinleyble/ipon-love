package com.iponlove.app.feature.widget.domain.usecase

/**
 * Pure "should show" decision for the Records-tab widget-adoption nudge card (Item 11): visible
 * only for a non-adopter, and only if it's never been shown or was last shown [CADENCE_DAYS]+
 * days ago. Extracted from [com.iponlove.app.feature.transactions.presentation.TransactionsViewModel]
 * so the cadence boundary is unit-testable without Glance or DataStore.
 */
object WidgetNudgeVisibility {
    const val CADENCE_DAYS = 30L
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun shouldShow(adopted: Boolean, lastShownAtMillis: Long?, nowMillis: Long): Boolean {
        if (adopted) return false
        if (lastShownAtMillis == null) return true
        return (nowMillis - lastShownAtMillis) >= CADENCE_DAYS * DAY_MILLIS
    }
}
