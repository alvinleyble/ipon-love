package com.iponlove.app.core.sync

import android.util.Log
import com.iponlove.app.core.sync.data.ClockOffsetStore
import com.iponlove.app.core.sync.data.SyncStatusStore
import kotlinx.coroutines.CompletableDeferred
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
    private val fullSyncSteps: Set<FullSyncStep> = emptySet(),
    private val clock: SyncClock? = null,
    private val clockOffsetStore: ClockOffsetStore? = null,
    private val serverTimeFetcher: (suspend () -> Instant)? = null,
    private val syncStatusStore: SyncStatusStore? = null,
    private val now: () -> Instant = Instant::now,
) {
    /** Stable FK order regardless of DI contribution order (ADR-0009). */
    private val ordered: List<TableSyncer> = syncers.sortedBy { it.table.ordinal }

    private val inFlight = Mutex()

    // The in-flight (or just-completed) sync's terminal result, published for callers whose
    // own sync() call coalesces (F4): they await the *actual* outcome of the run they raced
    // with instead of sampling [state], which can be stale (a prior user's/prior run's
    // Success) or premature (still Syncing because this caller's own attempt lost the race).
    // Cleared whenever pushOnly()/pullOnly() take the lock so a coalescing sync() never reports
    // an unrelated older sync's result as if it were the outcome of the run it just raced with.
    @Volatile private var currentRun: CompletableDeferred<Boolean>? = null

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /**
     * Run one full sync. Coalesces with any in-flight run: if one is already running, this
     * call awaits *that* run's terminal result (throwing if it failed) instead of returning
     * immediately, so every caller — whether it won the race or not — sees the actual outcome
     * of the run it participated in (F4). Returns true if that run succeeded.
     */
    suspend fun sync(): Boolean {
        if (!inFlight.tryLock()) return currentRun?.await() ?: false
        val deferred = CompletableDeferred<Boolean>()
        currentRun = deferred
        try {
            _state.value = SyncState.Syncing
            // Advisory full-sync-only steps first (paywall entitlement reconcile + remote config
            // refresh, S4). Guarded individually: these are best-effort and their write (a dirty
            // users row from the reconcile) must still be picked up by the push below, but a
            // Play/network failure here must never abort the data sync. Runs only in sync(), not
            // pushOnly()/pullOnly() — see FullSyncStep — so Play billing IPC never fires on a
            // keystroke-debounced micro-push.
            for (step in fullSyncSteps) runCatching { step.run() }
            // Upload pending files before pushing rows so rows carry the Storage URL.
            for (step in preSyncSteps) step.run()
            // Push parent→child (sequential, FK-ordered) so the server sees parents first.
            // Per-table isolation: one table's rejected push (e.g. an RLS reject on a
            // goal_contribution whose goal was unshared out from under a pending offline row —
            // F1) must NOT abort the run before the pull phase, or the client never learns the
            // goal is gone and the poisoned row wedges every subsequent sync forever. Remember
            // the first failure, keep pushing the rest, still pull, then rethrow so error/retry
            // semantics are unchanged (ADR-0002, ADR-0009).
            val pushError = pushAllIsolated()
            // Pull all tables in parallel — independent SELECTs, each manages its own cursor.
            pullAllParallel()
            calibrateClock()
            pushError?.let { throw it }
            val finishedAt = now()
            // Best-effort bookkeeping (Item 9): the sync itself succeeded, so a DataStore IO
            // hiccup here must not flip the run to Error.
            runCatching { syncStatusStore?.save(finishedAt) }
            _state.value = SyncState.Success(finishedAt)
            deferred.complete(true)
            return true
        } catch (t: Throwable) {
            // Log.w, not Log.d — Log.d is suppressed on some OEM ROMs (the test device), and the
            // UI shows friendly copy only (Item 9), so this is the one place the raw cause lands.
            Log.w(TAG, "Full sync failed", t)
            _state.value = SyncState.Error(t.message ?: t.javaClass.simpleName)
            deferred.completeExceptionally(t)
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
        // Not a full sync() run, so it has no terminal Boolean to publish — clear any stale
        // deferred so a coalescing sync() call doesn't await an unrelated older run's result.
        currentRun = null
        try {
            for (step in preSyncSteps) step.run()
            var pushedAnything = false
            var pushError: Throwable? = null
            // Same per-table isolation as [sync] (F1): a rejected table can't stop the others
            // from pushing; remember the first failure and rethrow after attempting all tables.
            for (syncer in ordered) {
                try {
                    if (syncer.push()) pushedAnything = true
                } catch (t: Throwable) {
                    if (pushError == null) pushError = t
                }
            }
            pushError?.let { throw it }
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
        // Not a full sync() run — see [pushOnly]'s comment.
        currentRun = null
        try {
            pullAllParallel()
            return true
        } finally {
            inFlight.unlock()
        }
    }

    /**
     * Push every table in FK order, isolating each table's failure: a rejected push is caught so
     * the remaining (and, in [sync], the pull) still run. Returns the FIRST failure, or null when
     * every table pushed cleanly — the caller rethrows it to preserve error/retry semantics.
     */
    private suspend fun pushAllIsolated(): Throwable? {
        var pushError: Throwable? = null
        for (syncer in ordered) {
            try {
                syncer.push()
            } catch (t: Throwable) {
                if (pushError == null) pushError = t
            }
        }
        return pushError
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

    private companion object {
        const val TAG = "SyncEngine"
    }
}
