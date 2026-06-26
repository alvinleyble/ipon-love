package com.iponlove.app.core.sync

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Realtime-backed [CoupleBell] (ADR-0015). The channel is **private** (`isPrivate = true`), so
 * the Realtime server enforces the RLS policy on `realtime.messages` keyed to
 * `auth_couple_id()` — only the two couple members can subscribe or broadcast on
 * `couple:{coupleId}`. `receiveOwnBroadcasts = false` is the self-echo guard: the writer never
 * reacts to its own ping, so a broadcast can't trigger the broadcaster's own pull.
 *
 * The websocket connects lazily on first [connect] (`connectOnSubscribe` default) and the
 * Realtime plugin authorises the private channel with the current Auth session token, so this
 * holds no token itself.
 */
class SupabaseCoupleBell(
    private val client: SupabaseClient,
    private val scope: CoroutineScope,
) : CoupleBell {

    private val _pings = MutableSharedFlow<Unit>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val pings: Flow<Unit> = _pings.asSharedFlow()

    /** Serialises connect/disconnect so a fast re-pair can't leave two channels live. */
    private val lifecycle = Mutex()
    private var channel: RealtimeChannel? = null
    private var pingJob: Job? = null

    override suspend fun connect(coupleId: String) = lifecycle.withLock {
        teardown()
        val ch = client.realtime.channel("couple:$coupleId") {
            isPrivate = true
            broadcast { receiveOwnBroadcasts = false }
        }
        pingJob = ch.broadcastFlow<JsonObject>(EVENT)
            .onEach {
                Log.d(TAG, "ping received on couple:$coupleId")
                _pings.emit(Unit)
            }
            .launchIn(scope)
        ch.subscribe(blockUntilSubscribed = true)
        channel = ch
    }

    override suspend fun disconnect() = lifecycle.withLock { teardown() }

    override suspend fun broadcast() {
        // Snapshot under the lock so we never broadcast on a half-torn-down channel.
        val ch = lifecycle.withLock { channel }
        if (ch == null) {
            Log.d(TAG, "broadcast skipped — no channel connected")
            return
        }
        ch.broadcast(EVENT, EMPTY_PING)
        Log.d(TAG, "broadcast ping sent")
    }

    private suspend fun teardown() {
        pingJob?.cancel()
        pingJob = null
        channel?.let { client.realtime.removeChannel(it) }
        channel = null
    }

    private companion object {
        const val TAG = "LiveSync"
        const val EVENT = "changed"
        val EMPTY_PING: JsonObject = buildJsonObject { }
    }
}
