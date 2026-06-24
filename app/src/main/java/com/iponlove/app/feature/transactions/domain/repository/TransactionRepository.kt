package com.iponlove.app.feature.transactions.domain.repository

import com.iponlove.app.feature.transactions.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Transactions source of truth (Room-backed). All writes funnel through here so the sync
 * bookkeeping — `updated_at` stamping (ADR-0001), `pending_sync` (ADR-0002), soft delete
 * (ADR-0010) — is applied in one place, and `recurring_rule_id` provenance is preserved
 * across edits.
 */
interface TransactionRepository {

    /** Active (non-deleted) transactions, most recent first. */
    fun observeTransactions(): Flow<List<Transaction>>

    suspend fun getTransaction(id: String): Transaction?

    suspend fun upsertTransaction(transaction: Transaction)

    /** Soft delete — sets `is_deleted = true`; never a hard delete (ADR-0010). */
    suspend fun deleteTransaction(id: String)
}
