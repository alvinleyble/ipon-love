package com.iponlove.app.feature.accounts.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.accounts.data.local.AccountDao
import com.iponlove.app.feature.accounts.data.local.AccountEntity
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [AccountRepository]. The single place every account write applies the
 * sync bookkeeping: each mutation stamps a fresh monotonic `updated_at` (ADR-0001) and
 * raises `pending_sync` (ADR-0002); deletes are soft (ADR-0010).
 */
class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao,
    private val clock: SyncClock,
    private val currentUser: CurrentUserProvider,
) : AccountRepository {

    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> =
        dao.observeAccounts(includeArchived).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAccount(id: String): Account? = dao.getById(id)?.toDomain()

    override suspend fun upsertAccount(account: Account) {
        val existing = dao.getById(account.id)
        val updatedAt = clock.stamp(existing?.updatedAt)
        dao.upsert(
            AccountEntity(
                id = account.id,
                userId = existing?.userId ?: currentUser.userId(),
                name = account.name,
                type = account.type,
                openingBalance = account.openingBalance,
                icon = account.icon,
                color = account.color,
                position = account.position,
                isArchived = account.isArchived,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                isArchived = archived,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
    }

    override suspend fun deleteAccount(id: String) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
    }
}
