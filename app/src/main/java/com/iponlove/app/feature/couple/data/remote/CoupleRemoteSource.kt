package com.iponlove.app.feature.couple.data.remote

import javax.inject.Inject

/**
 * Supabase side of couples. Beyond the standard sync push/pull, it exposes the four
 * pairing RPCs (ADR-0006, ADR-0008) — couples are never written directly by the client,
 * so all mutation flows through these SECURITY DEFINER functions.
 */
interface CoupleRemoteSource {
    suspend fun push(rows: List<CoupleDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<CoupleDto>

    /** Create a couple for the caller (becomes user1). Returns the new couple id. */
    suspend fun createCouple(name: String): String

    /** Join a couple by its invite code (caller becomes user2). Returns the couple id. */
    suspend fun redeemInvite(code: String): String

    /** Rotate the caller's couple invite code. Returns the new code. */
    suspend fun rotateInviteCode(): String

    /** Dissolve the caller's couple. */
    suspend fun unpair()

    /** Set (or clear, with null) the couple's shared banner photo URL (v1.7.0 Item 10). */
    suspend fun setCoupleBanner(url: String?)
}

/** No-op fallback (push/pull only); pairing RPCs are unsupported offline. */
class StubCoupleRemoteSource @Inject constructor() : CoupleRemoteSource {
    override suspend fun push(rows: List<CoupleDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<CoupleDto> = emptyList()
    override suspend fun createCouple(name: String): String = error("not supported")
    override suspend fun redeemInvite(code: String): String = error("not supported")
    override suspend fun rotateInviteCode(): String = error("not supported")
    override suspend fun unpair() = error("not supported")
    override suspend fun setCoupleBanner(url: String?) = error("not supported")
}
