package com.iponlove.app.feature.widget.domain.usecase

import androidx.glance.appwidget.GlanceAppWidgetManager
import com.iponlove.app.feature.widget.presentation.AddTransactionWidget
import com.iponlove.app.feature.widget.presentation.BalanceWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject

/**
 * True once either home-screen widget (balance or quick-add) has at least one instance placed.
 * Backs the Records-tab widget-adoption nudge card (Item 11). A one-shot suspend check, not a
 * Flow — Glance exposes no signal to observe adoption changes, so callers re-check on each fresh
 * collection instead (matches [com.iponlove.app.feature.widget.presentation.Widgets]'s existing
 * un-tested `GlanceAppWidgetManager` calls).
 */
class CheckWidgetAdoptionUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend operator fun invoke(): Boolean {
        val manager = GlanceAppWidgetManager(context)
        return manager.getGlanceIds(BalanceWidget::class.java).isNotEmpty() ||
            manager.getGlanceIds(AddTransactionWidget::class.java).isNotEmpty()
    }
}
