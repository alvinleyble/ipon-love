package com.iponlove.app.feature.settings.domain.model

/**
 * Live counts shown on the Reset finances confirm dialog (ADR-0037) — what the wipe touches:
 * how many transactions get soft-deleted and how many personal account balances get zeroed.
 * Everything else (categories, budgets, recurring rules, savings goals + their contributions,
 * notes, all couple/shared state) survives and is deliberately not counted here.
 */
data class ResetFinancesCounts(
    val transactions: Int,
    val accounts: Int,
)
