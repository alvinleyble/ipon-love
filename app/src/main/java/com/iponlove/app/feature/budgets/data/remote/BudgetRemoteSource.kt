package com.iponlove.app.feature.budgets.data.remote

import javax.inject.Inject

/**
 * The Supabase side of budgets sync. A port so the engine never depends on the Supabase
 * SDK directly; the real implementation lands with the backend slice.
 */
interface BudgetRemoteSource {
    suspend fun push(rows: List<BudgetDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<BudgetDto>
}

/** No-op remote for offline development — rows stay `pending_sync` until the real backend. */
class StubBudgetRemoteSource @Inject constructor() : BudgetRemoteSource {
    override suspend fun push(rows: List<BudgetDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<BudgetDto> = emptyList()
}
