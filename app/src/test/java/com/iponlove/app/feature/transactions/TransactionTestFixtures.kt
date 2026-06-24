package com.iponlove.app.feature.transactions

import com.iponlove.app.feature.transactions.data.local.TransactionDao
import com.iponlove.app.feature.transactions.data.local.TransactionEntity
import com.iponlove.app.feature.transactions.data.remote.TransactionDto
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant

/** In-memory [TransactionDao] mirroring the real query semantics for fast JVM tests. */
class FakeTransactionDao : TransactionDao {
    val store = linkedMapOf<String, TransactionEntity>()
    private val changes = MutableStateFlow(0)

    override fun observeTransactions(userId: String): Flow<List<TransactionEntity>> =
        changes.map {
            store.values
                .filter { it.userId == userId && !it.isDeleted }
                .sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.createdAt })
        }

    override fun observeCombined(): Flow<List<TransactionEntity>> =
        changes.map {
            store.values
                .filter { !it.isDeleted && !it.isPrivate }
                .sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.createdAt })
        }

    override suspend fun getById(id: String): TransactionEntity? = store[id]

    override suspend fun deleteById(id: String) {
        store.remove(id)
        changes.value++
    }

    override suspend fun deleteNotOwnedBy(userId: String) {
        store.values.removeAll { it.userId != userId }
        changes.value++
    }

    override suspend fun upsert(transaction: TransactionEntity) {
        store[transaction.id] = transaction
        changes.value++
    }

    override suspend fun dirtyRows(): List<TransactionEntity> = store.values.filter { it.pendingSync }

    override suspend fun clearPending(ids: List<String>) {
        ids.forEach { id -> store[id]?.let { store[id] = it.copy(pendingSync = false) } }
        changes.value++
    }

    override suspend fun applyPullBatch(transactions: List<TransactionEntity>) {
        transactions.forEach { store[it.id] = it }
        changes.value++
    }
}

/** Domain transaction builder. */
fun txn(
    id: String,
    type: TransactionType,
    amount: String,
    accountId: String = "acc-1",
    toAccountId: String? = null,
    categoryId: String? = "cat-1",
    date: Instant = Instant.ofEpochMilli(1_000),
) = Transaction(
    id = id,
    type = type,
    amount = BigDecimal(amount),
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
    date = date,
)

fun transactionEntity(
    id: String,
    type: TransactionType = TransactionType.EXPENSE,
    amount: String = "100.00",
    accountId: String = "acc-1",
    toAccountId: String? = null,
    categoryId: String? = "cat-1",
    note: String? = null,
    date: Instant = Instant.ofEpochMilli(1_000),
    isPrivate: Boolean = false,
    recurringRuleId: String? = null,
    userId: String = "user-1",
    createdAt: Instant = Instant.ofEpochMilli(1_000),
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
    pendingSync: Boolean = false,
) = TransactionEntity(
    id = id,
    userId = userId,
    type = type,
    amount = BigDecimal(amount),
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    note = note,
    date = date,
    isPrivate = isPrivate,
    recurringRuleId = recurringRuleId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = pendingSync,
)

fun transactionDto(
    id: String,
    type: TransactionType = TransactionType.EXPENSE,
    amount: String = "100.00",
    accountId: String = "acc-1",
    serverRev: Long? = null,
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
) = TransactionDto(
    id = id,
    userId = "user-1",
    type = type,
    amount = BigDecimal(amount),
    accountId = accountId,
    toAccountId = null,
    categoryId = "cat-1",
    note = null,
    date = Instant.ofEpochMilli(1_000),
    isPrivate = false,
    recurringRuleId = null,
    createdAt = Instant.ofEpochMilli(1_000),
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)
