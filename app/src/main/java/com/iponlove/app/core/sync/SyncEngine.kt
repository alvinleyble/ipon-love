package com.iponlove.app.core.sync

import com.iponlove.app.core.sync.data.ClockOffsetStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * Orchestrates a full sync over every [TableSyncer], push-all then pull-all.
 * (ADR-0002, ADR-0009). This is the in-process interactive path (foreground, pull-to-
 * refresh, reconnect); WorkManager owns background retry and wraps a call to [sync]
 * (ADR-0012).
 *
 * **Push is sequential, FK-ordered** — a parent row must land on the server before its
 * child, otherwise the server's RLS/FK rejects the child.
 *
 * **Pull is parallel** — all tables fire their SELECT simultaneously; Room has no FK
 * constraints at the entity level, so parallel upserts are safe. On a steady-state sync
 * most tables return 0 rows (cursor already at latest), so 15 sequential ~600 ms round
 * trips collapse to ~1 parallel batch, cutting pull time from ~10 s to ~1 s.
 *
 * **Single-flight:** overlapping triggers coalesce — if a sync is in flight, a concurrent
 * call returns immediately (ADR-0015).
 *
 * @param syncers contributed by feature modules in any order; sorted here by FK order.
 */
class SyncEngine(
    syncers: Set<TableSyncer>,
    private val preSyncSteps: Set<PreSyncStep> = emptySet(),
    private val clock: SyncClock? = null,
    private val clockOffsetStore: ClockOffsetStore? = null,
    private val serverTimeFetcher: (suspend () -> Instant)? = null,
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
            // Upload pending files before pushing rows so rows carry the Storage URL.
            for (step in preSyncSteps) step.run()
            // Push parent→child (sequential, FK-ordered) so the server sees parents first.
            for (syncer in ordered) syncer.push()
            // Pull all tables in parallel — independent SELECTs, each manages its own cursor.
            pullAllParallel()
            calibrateClock()
            _state.value = SyncState.Success(now())
            return true
        } catch (t: Throwable) {
            _state.value = SyncState.Error(t.message ?: t.javaClass.simpleName)
            throw t
        } finally {
            inFlight.unlock()
        }
    }

    /**
     * Push half only, in FK order (ADR-0009) — the debounced write-on-push path (ADR-0015).
     * Runs pre-sync steps (e.g. image upload) first, like [sync], so a freshly written row
     * carries its Storage URL. Coalesces with any in-flight run via the shared [inFlight]
     * mutex: if a full [sync] or another narrow pass is already running, this is a no-op.
     *
     * Deliberately silent — it does NOT touch [state] or calibrate the clock; it's a
     * background micro-sync, not a user-visible refresh, so the UI spinner never flickers on
     * a debounced keystroke push.
     *
     * @return true only when at least one table actually sent rows — the caller rings the
     *   couple "bell" on true. False also covers "coalesced away" (lock not acquired), so a
     *   ping never fires for a push that didn't happen.
     */
    suspend fun pushOnly(): Boolean {
        if (!inFlight.tryLock()) return false
        try {
            for (step in preSyncSteps) step.run()
            var pushedAnything = false
            for (syncer in ordered) {
                if (syncer.push()) pushedAnything = true
            }
            return pushedAnything
        } finally {
            inFlight.unlock()
        }
    }

    /**
     * Pull half only, parallel — the bell-triggered receive path (ADR-0015).
     * All partner data still flows through the RLS-protected redacting-view syncers
     * (ADR-0005); the bell ping carried no data. Pulled rows land with `pending_sync = false`,
     * so a pull never makes a row dirty and thus never triggers a push or another bell — no
     * ping-pong. Coalesces with any in-flight run via [inFlight]; silent like [pushOnly].
     *
     * @return true if this call ran the pull; false if it coalesced with an in-flight run.
     */
    suspend fun pullOnly(): Boolean {
        if (!inFlight.tryLock()) return false
        try {
            pullAllParallel()
            return true
        } finally {
            inFlight.unlock()
        }
    }

    /** Fires all table pulls concurrently and waits for every one to finish. */
    private suspend fun pullAllParallel() = coroutineScope {
        for (syncer in ordered) launch { syncer.pull() }
    }

    private suspend fun calibrateClock() {
        if (clock == null || clockOffsetStore == null || serverTimeFetcher == null) return
        val serverNow = serverTimeFetcher.invoke()
        clock.recordServerTime(serverNow)
        clockOffsetStore.save(clock)
    }
}
