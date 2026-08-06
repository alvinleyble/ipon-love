package com.iponlove.app.feature.drafts

import com.iponlove.app.feature.drafts.data.local.TransactionDraftDao
import com.iponlove.app.feature.drafts.data.local.TransactionDraftEntity
import com.iponlove.app.feature.drafts.data.remote.TransactionDraftDto
import com.iponlove.app.feature.drafts.data.remote.TransactionDraftRemoteSource
import com.iponlove.app.feature.drafts.domain.model.TransactionDraft
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant

/** In-memory [TransactionDraftDao] for fast JVM tests. */
class FakeTransactionDraftDao : TransactionDraftDao {
    val store = linkedMapOf<String, TransactionDraftEntity>()
    private val changes = MutableStateFlow(0)

    override fun observeDrafts(userId: String): Flow<List<TransactionDraftEntity>> =
        changes.map {
            store.values.filter { it.userId == userId && !it.isDeleted }.sortedBy { it.createdAt }
        }

    override fun observeDraftCount(userId: String): Flow<Int> =
        changes.map { store.values.count { it.userId == userId && !it.isDeleted } }

    override suspend fun getById(id: String): TransactionDraftEntity? = store[id]

    override suspend fun activeDrafts(): List<TransactionDraftEntity> =
        store.values.filter { !it.isDeleted }

    override suspend fun upsert(row: TransactionDraftEntity) {
        store[row.id] = row
        changes.value++
    }

    override suspend fun upsertAll(rows: List<TransactionDraftEntity>) {
        rows.forEach { store[it.id] = it }
        changes.value++
    }

    override suspend fun dirtyRows(): List<TransactionDraftEntity> =
        store.values.filter { it.pendingSync }

    override suspend fun clearPending(ids: List<String>) {
        ids.forEach { id -> store[id]?.let { store[id] = it.copy(pendingSync = false) } }
        changes.value++
    }
}

/** In-memory [TransactionDraftRemoteSource]. */
class FakeTransactionDraftRemote : TransactionDraftRemoteSource {
    val pushed = mutableListOf<TransactionDraftDto>()
    val serverRows = mutableListOf<TransactionDraftDto>()

    override suspend fun push(rows: List<TransactionDraftDto>): List<String> {
        pushed += rows
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<TransactionDraftDto> =
        serverRows.filter { (it.serverRev ?: 0L) > cursor }.sortedBy { it.serverRev }.take(limit)
}

fun draftEntity(
    id: String,
    userId: String = "user-1",
    type: TransactionType? = TransactionType.EXPENSE,
    amount: BigDecimal? = BigDecimal("120.50"),
    categoryId: String? = "cat-1",
    accountId: String? = "acc-1",
    toAccountId: String? = null,
    note: String? = "SM Supermarket",
    date: Instant? = Instant.ofEpochMilli(5_000),
    isPrivate: Boolean = false,
    receiptCount: Int = 0,
    localImageIds: List<String> = emptyList(),
    createdAt: Instant = Instant.ofEpochMilli(1_000),
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
    pendingSync: Boolean = false,
) = TransactionDraftEntity(
    id = id,
    userId = userId,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    toAccountId = toAccountId,
    note = note,
    date = date,
    isPrivate = isPrivate,
    receiptCount = receiptCount,
    localImageIds = localImageIds,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = pendingSync,
)

fun draftDto(
    id: String,
    userId: String = "user-1",
    amount: BigDecimal? = BigDecimal("120.50"),
    note: String? = "SM Supermarket",
    receiptCount: Int = 0,
    createdAt: Instant = Instant.ofEpochMilli(1_000),
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
) = TransactionDraftDto(
    id = id,
    userId = userId,
    type = TransactionType.EXPENSE,
    amount = amount,
    categoryId = "cat-1",
    accountId = "acc-1",
    toAccountId = null,
    note = note,
    date = Instant.ofEpochMilli(5_000),
    isPrivate = false,
    receiptCount = receiptCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

fun draft(
    id: String = "draft-1",
    type: TransactionType? = TransactionType.EXPENSE,
    amount: BigDecimal? = BigDecimal("120.50"),
    categoryId: String? = "cat-1",
    accountId: String? = "acc-1",
    note: String? = "SM Supermarket",
    receiptCount: Int = 0,
    localImageIds: List<String> = emptyList(),
    parkedAt: Instant = Instant.ofEpochMilli(1_000),
) = TransactionDraft(
    id = id,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    note = note,
    date = Instant.ofEpochMilli(5_000),
    receiptCount = receiptCount,
    localImageIds = localImageIds,
    parkedAt = parkedAt,
)
