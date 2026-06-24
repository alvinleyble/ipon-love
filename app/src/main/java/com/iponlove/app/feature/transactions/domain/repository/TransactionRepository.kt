package com.iponlove.app.feature.transactions.domain.repository

import com.iponlove.app.feature.transactions.domain.model.OwnedTransaction
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

    /**
     * The couple's shared ledger: both members' active, non-private transactions tagged
     * with their owner, most recent first. Drives the combined view (ADR-0011).
     */
    fun observeCombinedTransactions(): Flow<List<OwnedTransaction>>

    suspend fun getTransaction(id: String): Transaction?

    suspend fun upsertTransaction(transaction: Transaction)

    /** Soft delete — sets `is_deleted = true`; never a hard delete (ADR-0010). */
    suspend fun deleteTransaction(id: String)

    /**
     * Insert a recurring-rule-generated transaction, idempotently. Returns false WITHOUT
     * writing if a row with [transaction.id] already exists — active OR tombstoned — so a
     * deleted occurrence is never resurrected and a repeated materialization pass can't
     * duplicate. [transaction.id] must be the rule's deterministic occurrence id and
     * [recurringRuleId] is stamped as the row's provenance.
     */
    suspend fun materializeTransaction(transaction: Transaction, recurringRuleId: String): Boolean

    /** Hard-delete all replicated partner transactions on unpair (ADR-0008). */
    suspend fun purgePartnerData()
}
