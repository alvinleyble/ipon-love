package com.iponlove.app.feature.budgets.domain.usecase

/**
 * A stable key for one budget "line" — a (category, scope) pair — that survives month rollover,
 * unlike [com.iponlove.app.feature.budgets.domain.model.Budget.id] which is a fresh row every
 * month. Backs the local per-budget mute (ADR-0054 decision 6): the mute must key on something
 * that doesn't reset when next month's row is created.
 */
object BudgetLineId {
    fun of(categoryId: String?, isShared: Boolean): String =
        "${if (isShared) "shared" else "personal"}:${categoryId ?: "overall"}"
}
