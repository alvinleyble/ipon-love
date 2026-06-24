package com.iponlove.app.feature.transactions.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.transactions.data.local.TransactionDao
import com.iponlove.app.feature.transactions.data.local.TransactionEntity
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
) : TransactionRepository {

    override fun observeTransactions(): Flow<List<Transaction>> =
        dao.observeTransactions(currentUser.userId())
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getTransaction(id: String): Transaction? = dao.getById(id)?.toDomain()

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
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
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
        return true
    }

    override suspend fun purgePartnerData() = dao.deleteNotOwnedBy(currentUser.userId())
}
