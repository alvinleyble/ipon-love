package com.iponlove.app.feature.accounts.domain.repository

import com.iponlove.app.feature.accounts.domain.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * Accounts source of truth (Room-backed). All writes go through here so the sync
 * bookkeeping — `updated_at` stamping (ADR-0001), `pending_sync` (ADR-0002), and soft
 * delete (ADR-0010) — is applied in exactly one place.
 */
interface AccountRepository {

    /** Active (non-deleted) accounts, ordered by position; archived included only if asked. */
    fun observeAccounts(includeArchived: Boolean = false): Flow<List<Account>>

    suspend fun getAccount(id: String): Account?

    /** Create (id unseen) or edit (id exists) an account. */
    suspend fun upsertAccount(account: Account)

    suspend fun setArchived(id: String, archived: Boolean)

    /** Soft delete — sets `is_deleted = true`; never a hard delete (ADR-0010). */
    suspend fun deleteAccount(id: String)
}
