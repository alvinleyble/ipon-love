package com.iponlove.app.feature.settings.domain.repository

import com.iponlove.app.feature.settings.domain.model.ResetFinancesCounts

/**
 * "Restart fresh" (ADR-0037): wipes only the signed-in user's own money-movement rows —
 * transactions, recurring rules, budgets, and goal contributions. Accounts, categories,
 * savings-goal definitions, opening balances, notes, and all couple/shared state survive.
 */
interface ResetFinancesRepository {

    /** Fresh counts of what [reset] would tombstone, for the confirm dialog's summary. */
    suspend fun previewCounts(): ResetFinancesCounts

    /** Soft-deletes every owned row across the four tables atomically, then pushes. */
    suspend fun reset()
}
