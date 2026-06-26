package com.iponlove.app.core.sync

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The write→push signal bus (ADR-0015, fix A). Every repository write that produces a dirty
 * row calls [requestPush]; a single debounced collector (CoupleChannelManager) coalesces a
 * burst of edits into one [SyncEngine.pushOnly] so own writes reach the server in ~2 s
 * instead of waiting for the next full sync.
 *
 * Deliberately a fire-and-forget [MutableSharedFlow] with a 1-slot conflating buffer: a write
 * never suspends on it, and rapid writes collapse into a single pending signal that the
 * downstream debounce flattens anyway. It carries no payload — the push always walks every
 * dirty table in FK order (a new account + its transaction must push parent-before-child), so
 * *which* table was written is irrelevant.
 *
 * Pull-applied rows land with `pending_sync = false` and never go through a repository write,
 * so they never call [requestPush] — that's the structural half of the no-ping-pong guarantee.
 */
@Singleton
class SyncTrigger @Inject constructor() {

    private val _signals = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits once per write burst (conflated). Collected by CoupleChannelManager, debounced. */
    val signals: Flow<Unit> = _signals.asSharedFlow()

    /** Signal that a local dirty row was just written. Non-suspending, never throws. */
    fun requestPush() {
        _signals.tryEmit(Unit)
    }

    companion object {
        /**
         * A shared no-op instance for unit tests / manual construction that don't exercise the
         * live-sync path. Hilt always injects the real [Singleton]; this only backs the default
         * constructor argument on repositories so their tests need not supply a trigger.
         */
        val NONE = SyncTrigger()
    }
}
