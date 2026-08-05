package com.iponlove.app.feature.transactions.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.session.LastActiveUserStore
import com.iponlove.app.core.session.userIdOrNull
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.feature.transactions.data.local.TransactionDao
import com.iponlove.app.feature.transactions.data.local.TransactionEntity
import com.iponlove.app.feature.transactions.domain.model.OwnedTransaction
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * Room-backed [TransactionRepository]. The single place every transaction write applies
 * the sync bookkeeping: a fresh monotonic `updated_at` (ADR-0001) and `pending_sync`
 * (ADR-0002); deletes are soft (ADR-0010). Ownership and `recurring_rule_id` provenance
 * survive edits.
 */
class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
    private val clock: SyncClock,
    private val currentUser: CurrentUserProvider,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
    private val lastActiveUser: LastActiveUserStore = LastActiveUserStore.NONE,
) : TransactionRepository {

    // userId resolved inside each flow, not eagerly: these are re-collected during the
    // sign-out transition (auth already null) where an eager userId() would crash the process.
    override fun observeTransactions(): Flow<List<Transaction>> = flow {
        val userId = currentUser.userIdOrNull()
        if (userId == null) emit(emptyList())
        else emitAll(dao.observeTransactions(userId).map { rows -> rows.map { it.toDomain() } })
    }

    override fun observeTransactions(startInclusive: Instant, endExclusive: Instant): Flow<List<Transaction>> = flow {
        val userId = currentUser.userIdOrNull()
        if (userId == null) emit(emptyList())
        else emitAll(
            dao.observeTransactions(userId, startInclusive, endExclusive)
                .map { rows -> rows.map { it.toDomain() } },
        )
    }

    override fun observeHasAnyTransaction(): Flow<Boolean> = flow {
        val userId = currentUser.userIdOrNull()
        if (userId == null) emit(false)
        else emitAll(dao.observeHasAnyTransaction(userId))
    }

    // Not user-scoped: recurring rules are owner-only (ADR-0009), so the only occurrence ids
    // that can ever match a rule's deterministic `ruleId:date` id are the current user's own.
    override fun observeMaterializedRecurringIds(): Flow<Set<String>> =
        dao.observeRecurringOccurrenceIds().map { it.toSet() }

    override fun observeCombinedTransactions(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<OwnedTransaction>> =
        dao.observeCombined(startInclusive, endExclusive)
            .map { rows -> rows.map { OwnedTransaction(ownerId = it.userId, transaction = it.toDomain()) } }

    override fun observeCombinedTransactionsUnbounded(): Flow<List<Transaction>> =
        dao.observeCombinedUnbounded().map { rows -> rows.map { it.toDomain() } }

    override fun observeHasAnyCombinedTransaction(): Flow<Boolean> = dao.observeHasAnyCombinedTransaction()

    // Balance ledger falls back to the persisted last-active id when the live session is null
    // (Item 10 follow-up) so the home-screen widget's balance math works on a cold/backgrounded
    // process — the accounts read has the same fallback; both must agree or the rows wouldn't sum.
    override fun observeBalanceLedger(): Flow<List<Transaction>> = flow {
        val userId = currentUser.userIdOrNull() ?: lastActiveUser.lastUserId()
        if (userId == null) emit(emptyList())
        else emitAll(dao.observeForBalances(userId).map { rows -> rows.map { it.toDomain() } })
    }

    override suspend fun getTransaction(id: String): Transaction? = dao.getById(id)?.toDomain()

    override suspend fun getActiveTransactions(ids: Collection<String>): List<Transaction> =
        if (ids.isEmpty()) emptyList() else dao.getActiveByIds(ids.toList()).map { it.toDomain() }

    override suspend fun countByCategory(categoryId: String): Int = dao.countByCategory(categoryId)

    override suspend fun countByAccount(accountId: String): Int = dao.countByAccount(accountId)

    override suspend fun upsertTransaction(transaction: Transaction) {
        val existing = dao.getById(transaction.id)
        val updatedAt = clock.stamp(existing?.updatedAt)
        dao.upsert(
            TransactionEntity(
                id = transaction.id,
                userId = existing?.userId ?: currentUser.userId(),
                type = transaction.type,
                amount = transaction.amount,
                accountId = transaction.accountId,
                toAccountId = transaction.toAccountId,
                categoryId = transaction.categoryId,
                note = transaction.note,
                date = transaction.date,
                isPrivate = transaction.isPrivate,
                recurringRuleId = existing?.recurringRuleId,
                isSettlement = transaction.isSettlement,
                isAdjustment = transaction.isAdjustment,
                transferFeeTransactionId = transaction.transferFeeTransactionId,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun deleteTransaction(id: String) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun materializeTransaction(
        transaction: Transaction,
        recurringRuleId: String,
    ): Boolean {
        // Insert-if-absent on the deterministic id: an existing row — active or tombstoned —
        // means this occurrence was already materialized (or deleted), so do nothing.
        if (dao.getById(transaction.id) != null) return false
        val updatedAt = clock.stamp(null)
        dao.upsert(
            TransactionEntity(
                id = transaction.id,
                userId = currentUser.userId(),
                type = transaction.type,
                amount = transaction.amount,
                accountId = transaction.accountId,
                toAccountId = null,
                categoryId = transaction.categoryId,
                note = transaction.note,
                date = transaction.date,
                isPrivate = transaction.isPrivate,
                recurringRuleId = recurringRuleId,
                createdAt = updatedAt,
                updatedAt = updatedAt,
                isDeleted = false,
                serverRev = null,
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
        return true
    }

    override suspend fun purgePartnerData() = dao.deleteNotOwnedBy(currentUser.userId())
}
