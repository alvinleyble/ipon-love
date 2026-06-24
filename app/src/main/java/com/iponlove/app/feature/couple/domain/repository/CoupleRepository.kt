package com.iponlove.app.feature.couple.domain.repository

import com.iponlove.app.feature.couple.domain.model.Couple
import kotlinx.coroutines.flow.Flow

/**
 * Pairing operations. Every mutation is a server-side RPC followed by a sync so the new
 * state (couple row + updated users rows) replicates into local Room immediately; on
 * failure a [com.iponlove.app.feature.couple.domain.model.PairingException] is thrown.
 */
interface CoupleRepository {

    /** Observe the local couple row by id, or null if not yet replicated. */
    fun observeCouple(coupleId: String): Flow<Couple?>

    /** Create a couple for the current user (becomes user1). */
    suspend fun createCouple(name: String)

    /** Join a couple by invite code (current user becomes user2). */
    suspend fun redeemInvite(code: String)

    /** Rotate the current couple's invite code. */
    suspend fun rotateInviteCode()

    /** Dissolve the current couple. */
    suspend fun unpair()
}
