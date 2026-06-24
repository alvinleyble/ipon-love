package com.iponlove.app.feature.accounts.data.remote

import javax.inject.Inject

/**
 * The Supabase side of accounts sync. A port so the sync engine (and tests) never
 * depend on the Supabase SDK directly; the real implementation lands with the backend
 * slice. [StubAccountRemoteSource] keeps the app fully working offline until then.
 */
interface AccountRemoteSource {

    /** Upsert [rows] server-side; returns the ids the server acked. */
    suspend fun push(rows: List<AccountDto>): List<String>

    /** Fetch up to [limit] rows with `server_rev > cursor`, ordered by `server_rev`. */
    suspend fun pull(cursor: Long, limit: Int): List<AccountDto>
}

/**
 * No-op remote for offline development: nothing is acked (rows stay `pending_sync`,
 * ready to push once the real backend is wired) and nothing is pulled. Swapped out by
 * the Supabase implementation in the backend slice.
 */
class StubAccountRemoteSource @Inject constructor() : AccountRemoteSource {
    override suspend fun push(rows: List<AccountDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<AccountDto> = emptyList()
}
