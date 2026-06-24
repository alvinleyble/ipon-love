package com.iponlove.app.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import java.time.Instant

/** Observable state of the sync engine, shown subtly in the UI (ARCHITECTURE §6). */
sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Success(val at: Instant) : SyncState
    data class Error(val message: String) : SyncState
}

/**
 * Orchestrates a full sync over every [TableSyncer], in FK order, push-all then pull-all
 * (ADR-0002, ADR-0009). This is the in-process interactive path (foreground, pull-to-
 * refresh, reconnect); WorkManager owns background retry and wraps a call to [sync]
 * (ADR-0012).
 *
 * **Single-flight (ARCHITECTURE §6):** overlapping triggers coalesce — if a sync is in
 * flight, a concurrent [sync] call returns immediately rather than running a second pass.
 *
 * Progress is durable: per-row `pending_sync` is cleared as pushes ack and per-table
 * cursors advance as pull batches commit, so an interrupted run self-heals on the next
 * trigger (ADR-0009) — a thrown failure surfaces as [SyncState.Error] without rolling
 * back the work already committed.
 *
 * @param syncers contributed by feature modules in any order; sorted here by FK order.
 */
class SyncEngine(
    syncers: Set<TableSyncer>,
    private val now: () -> Instant = Instant::now,
) {
    /** Stable FK order regardless of DI contribution order (ADR-0009). */
    private val ordered: List<TableSyncer> = syncers.sortedBy { it.table.ordinal }

    private val inFlight = Mutex()

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /**
     * Run one full sync. Coalesces with any in-flight run (returns false if one was
     * already running); returns true if this call performed the sync.
     */
    suspend fun sync(): Boolean {
        if (!inFlight.tryLock()) return false
        try {
            _state.value = SyncState.Syncing
            // Push parent→child so a parent gets a lower server_rev than its child...
            for (syncer in ordered) syncer.push()
            // ...then pull parent→child so a child's parent is already present.
            for (syncer in ordered) syncer.pull()
            _state.value = SyncState.Success(now())
            return true
        } catch (t: Throwable) {
            _state.value = SyncState.Error(t.message ?: t.javaClass.simpleName)
            throw t
        } finally {
            inFlight.unlock()
        }
    }
}
