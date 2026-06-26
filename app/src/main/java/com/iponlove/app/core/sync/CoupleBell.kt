package com.iponlove.app.core.sync

import kotlinx.coroutines.flow.Flow

/**
 * The couple "bell" port (ADR-0015, fix B): a content-less Realtime Broadcast channel shared
 * by the two members of a couple. It is a *notification*, never a data channel — pings carry
 * no row data, so the redacting-view privacy guarantee (ADR-0005) is never bypassed; real
 * partner data still arrives only through the RLS-protected pull.
 *
 * Implemented by [SupabaseCoupleBell] over Realtime; faked in tests so the no-loop /
 * no-self-echo behaviour can be verified without a live socket.
 */
interface CoupleBell {

    /**
     * Pings received from the *partner* on the currently-connected channel. Own broadcasts are
     * excluded (`receiveOwnBroadcasts = false`) — the self-echo guard — so collecting this and
     * pulling can never loop back into a broadcast.
     */
    val pings: Flow<Unit>

    /** Subscribe to `couple:{coupleId}`. Tears down any previous channel first; idempotent. */
    suspend fun connect(coupleId: String)

    /** Leave the current channel, if any. Idempotent — safe to call when not connected. */
    suspend fun disconnect()

    /** Send a content-less "changed" ping to the partner. No-op when not connected. */
    suspend fun broadcast()
}
