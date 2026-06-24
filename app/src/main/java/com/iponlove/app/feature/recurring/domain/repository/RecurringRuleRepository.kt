package com.iponlove.app.feature.recurring.domain.repository

import com.iponlove.app.feature.recurring.domain.model.RecurringRule
import kotlinx.coroutines.flow.Flow

/**
 * Recurring rules source of truth (Room-backed). All writes funnel through here so the
 * sync bookkeeping — `updated_at` stamping (ADR-0001), `pending_sync` (ADR-0002), soft
 * delete (ADR-0010) — is applied in one place. Rules are owner-only, never shared.
 */
interface RecurringRuleRepository {

    /** Active (non-deleted) rules, newest first. */
    fun observeRules(): Flow<List<RecurringRule>>

    /** One-shot snapshot of active rules, for the materialization pass. */
    suspend fun activeRules(): List<RecurringRule>

    suspend fun getRule(id: String): RecurringRule?

    suspend fun upsertRule(rule: RecurringRule)

    /** Soft delete — sets `is_deleted = true`; never a hard delete (ADR-0010). */
    suspend fun deleteRule(id: String)
}
