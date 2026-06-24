package com.iponlove.app.feature.accounts

import com.iponlove.app.feature.accounts.data.local.AccountDao
import com.iponlove.app.feature.accounts.data.local.AccountEntity
import com.iponlove.app.feature.accounts.data.remote.AccountDto
import com.iponlove.app.feature.accounts.domain.model.AccountType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant

/** In-memory [AccountDao] mirroring the real query semantics for fast JVM tests. */
class FakeAccountDao : AccountDao {
    val store = linkedMapOf<String, AccountEntity>()
    private val changes = MutableStateFlow(0)

    override fun observeAccounts(includeArchived: Boolean): Flow<List<AccountEntity>> =
        changes.map {
            store.values
                .filter { !it.isDeleted && (includeArchived || !it.isArchived) }
                .sortedWith(compareBy({ it.position }, { it.createdAt }))
        }

    override suspend fun getById(id: String): AccountEntity? = store[id]

    override suspend fun upsert(account: AccountEntity) {
        store[account.id] = account
        changes.value++
    }

    override suspend fun dirtyRows(): List<AccountEntity> = store.values.filter { it.pendingSync }

    override suspend fun clearPending(ids: List<String>) {
        ids.forEach { id -> store[id]?.let { store[id] = it.copy(pendingSync = false) } }
        changes.value++
    }

    override suspend fun applyPullBatch(accounts: List<AccountEntity>) {
        accounts.forEach { store[it.id] = it }
        changes.value++
    }
}

fun accountEntity(
    id: String,
    name: String = "GCash",
    userId: String = "user-1",
    type: AccountType = AccountType.EWALLET,
    openingBalance: BigDecimal = BigDecimal("100.00"),
    position: Int = 0,
    isArchived: Boolean = false,
    createdAt: Instant = Instant.ofEpochMilli(1_000),
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
    pendingSync: Boolean = false,
) = AccountEntity(
    id = id,
    userId = userId,
    name = name,
    type = type,
    openingBalance = openingBalance,
    icon = null,
    color = null,
    position = position,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = pendingSync,
)

fun accountDto(
    id: String,
    name: String = "GCash",
    userId: String = "user-1",
    serverRev: Long? = null,
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
) = AccountDto(
    id = id,
    userId = userId,
    name = name,
    type = AccountType.EWALLET,
    openingBalance = BigDecimal("100.00"),
    icon = null,
    color = null,
    position = 0,
    isArchived = false,
    createdAt = Instant.ofEpochMilli(1_000),
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)
