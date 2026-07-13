package com.iponlove.app.feature.settings.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The personal "budget month starts on day N" preference (ADR-0046). Local-only — the partner
 * never sees personal budgets, so there is no synced column; a reinstall loses the pref and
 * budgets read as calendar months (day 1) until it is re-set (self-healing, spent is derived).
 */
interface BudgetStartDayRepository {
    /** Emits 1..31; defaults to 1 (calendar months) when unset. */
    fun observe(): Flow<Int>
    suspend fun setStartDay(day: Int)
}
