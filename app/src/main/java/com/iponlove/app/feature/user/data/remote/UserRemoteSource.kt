package com.iponlove.app.feature.user.data.remote

import javax.inject.Inject

/** Supabase side of users sync. Pull returns own row + partner row (RLS gates both). */
interface UserRemoteSource {
    suspend fun push(rows: List<UserDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<UserDto>
    /** Fetch a single user row by id, or null if not yet on the server (genuine new signup). */
    suspend fun fetchSelf(userId: String): UserDto?
}

class StubUserRemoteSource @Inject constructor() : UserRemoteSource {
    override suspend fun push(rows: List<UserDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<UserDto> = emptyList()
    override suspend fun fetchSelf(userId: String): UserDto? = null
}
