package com.iponlove.app.feature.transactions.domain.repository

import com.iponlove.app.feature.transactions.domain.model.OwnedTransaction
import com.iponlove.app.feature.transactions.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Transactions source of truth (Room-backed). All writes funnel through here so the sync
 * bookkeeping — `updated_at` stamping (ADR-0001), `pending_sync` (ADR-0002), soft delete
 * (ADR-0010) — is applied in one place, and `recurring_rule_id` provenance is preserved
 * across edits.
 */
interface TransactionRepository {

    /** Active (non-deleted) transactions, most recent first — unbounded (Analysis/Budgets do their own windowing). */
    fun observeTransactions(): Flow<List<Transaction>>

    /** Records' month-windowed view (ADR-0032) — bounded to [startInclusive, endExclusive) at the query layer. */
    fun observeTransactions(startInclusive: Instant, endExclusive: Instant): Flow<List<Transaction>>

    /** Whether the current user has ever recorded an active transaction (ADR-0032 empty-state signal). */
    fun observeHasAnyTransaction(): Flow<Boolean>

    /**
     * Deterministic ids of every occurrence already materialized from a recurring rule — active
     * OR tombstoned. Confirm-on-arrival (Item 37) subtracts this from a rule's due occurrences so
     * a confirmed (or confirmed-then-deleted) occurrence never resurfaces as "pending", even when
     * the rule cursor hasn't advanced past it (e.g. an out-of-order confirm).
     */
    fun observeMaterializedRecurringIds(): Flow<Set<String>>

    /**
     * The couple's shared ledger, month-windowed (ADR-0032): both members' active, non-private
     * transactions within [startInclusive, endExclusive), tagged with their owner, most recent
     * first. Drives the combined view (ADR-0011).
     */
    fun observeCombinedTransactions(startInclusive: Instant, endExclusive: Instant): Flow<List<OwnedTransaction>>

    /**
     * The couple's shared ledger, **unbounded** and ownerless — both members' active, non-private
     * transactions across all time as plain [Transaction]s. Feeds shared-budget spend + rollover in
     * the Budgets tab (Item 35): the rollover chain needs prior months' spend, so it can't use the
     * month-windowed [observeCombinedTransactions]; owner attribution isn't needed for a spend total.
     */
    fun observeCombinedTransactionsUnbounded(): Flow<List<Transaction>>

    /** Whether the couple has ever shared an active transaction (ADR-0032 empty-state signal). */
    fun observeHasAnyCombinedTransaction(): Flow<Boolean>

    /**
     * The ledger used to derive account balances, including couple-owned shared accounts: the
     * user's own transactions plus every member's non-private ones (ADR-0018). Feed alongside
     * the account opening balances to [com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator].
     */
    fun observeBalanceLedger(): Flow<List<Transaction>>

    suspend fun getTransaction(id: String): Transaction?

    /**
     * The still-active rows for [ids], in one read — feeds Records' bulk delete (v1.7.3 Item 7),
     * which has to inspect a whole selection before it writes anything. Already-tombstoned and
     * unknown ids are simply absent from the result.
     */
    suspend fun getActiveTransactions(ids: Collection<String>): List<Transaction>

    /**
     * The user's own recent note-bearing expenses, most recent first, capped at [limit] — the
     * corpus receipt-scan category/account inference matches a merchant against (v1.7.3 Item 2
     * Slice 2, ADR-0062 decision 5). Own rows only; empty when signed out.
     */
    suspend fun getOwnExpenseHistory(limit: Int): List<Transaction>

    /**
     * The user's own active expenses within [startInclusive, endExclusive) — the candidate set the
     * duplicate-scan warning narrows by amount and calendar day (ADR-0062 Consequences).
     */
    suspend fun getOwnExpensesBetween(startInclusive: Instant, endExclusive: Instant): List<Transaction>

    /** Active transactions referencing [categoryId] — feeds the category delete-confirm count (Item 5). */
    suspend fun countByCategory(categoryId: String): Int

    /**
     * Active transactions referencing [accountId] on either leg (incl. a transfer's destination) —
     * feeds the account delete-confirm count (Item 5).
     */
    suspend fun countByAccount(accountId: String): Int

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
