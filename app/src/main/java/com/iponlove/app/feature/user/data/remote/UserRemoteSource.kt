package com.iponlove.app.feature.user.data.remote

import javax.inject.Inject

/** Supabase side of users sync. Pull returns own row + partner row (RLS gates both). */
interface UserRemoteSource {
    /** Upsert the ordinary profile columns. Entitlement is NOT part of this (ADR-0060). */
    suspend fun push(rows: List<UserPushDto>): List<String>

    /**
     * Write the caller's entitlement through `set_self_entitlement` — the only path the
     * database still accepts for those four columns (ADR-0060). Idempotent server-side, so
     * calling it on every push is a no-op unless the values actually changed.
     */
    suspend fun writeEntitlement(write: UserEntitlementWrite)

    suspend fun pull(cursor: Long, limit: Int): List<UserDto>

    /** Fetch a single user row by id, or null if not yet on the server (genuine new signup). */
    suspend fun fetchSelf(userId: String): UserDto?
}

class StubUserRemoteSource @Inject constructor() : UserRemoteSource {
    override suspend fun push(rows: List<UserPushDto>): List<String> = emptyList()
    override suspend fun writeEntitlement(write: UserEntitlementWrite) = Unit
    override suspend fun pull(cursor: Long, limit: Int): List<UserDto> = emptyList()
    override suspend fun fetchSelf(userId: String): UserDto? = null
}
