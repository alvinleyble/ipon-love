package com.iponlove.app.core.sync

import android.util.Log
import com.iponlove.app.feature.couple.domain.model.PairingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the live-sync wiring for one process (ADR-0015). Lives in `core/` — not in feature or
 * activity code — per the "sharing logic stays generic, lives in core" scalability principle,
 * so future shared entities ride the same bell without rewiring.
 *
 * Three long-lived collectors, started once by [start]:
 *
 *  1. **Subscription lifecycle.** `combine(foreground, coupleId)` → subscribe to the couple
 *     [CoupleBell] only when *both* foregrounded and paired; disconnect when either drops;
 *     swap channels on re-pair. Single (unpaired) users never subscribe. While backgrounded
 *     the WorkManager periodic sync is the safety net (ADR-0012).
 *  2. **Bell → pull.** A partner ping (content-less) means "partner changed something" →
 *     debounced [SyncEngine.pullOnly] (pull only, through redacting views — ADR-0005). A burst
 *     of pings collapses into one pull; a ping during a full sync is dropped by the engine's
 *     single-flight mutex.
 *  3. **Write → push → bell.** A local dirty write ([SyncTrigger]) → debounced
 *     [SyncEngine.pushOnly]; if it actually sent rows *and* a channel is connected, ring the
 *     bell so the partner pulls. Pull-applied rows are never dirty, so a pull can't reach here
 *     → no ping-pong; the bell's own-echo is also off → no self-trigger.
 *
 * The pairing flow is gated on [setAuthenticatedUser] because [pairingStates] (backed by
 * ObservePairingStateUseCase) reads the signed-in user id eagerly and throws before sign-in —
 * so its flow is only built once an authenticated user id is present.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CoupleChannelManager(
    private val bell: CoupleBell,
    private val engine: SyncEngine,
    private val syncTrigger: SyncTrigger,
    private val pairingStates: () -> Flow<PairingState>,
    private val scope: CoroutineScope,
    private val pushDebounceMs: Long = 1_500,
    private val pullDebounceMs: Long = 1_000,
) {
    private val foreground = MutableStateFlow(false)
    private val authUserId = MutableStateFlow<String?>(null)
    private val started = AtomicBoolean(false)

    /** Fed by MainActivity's process-lifecycle observer (onStart/onStop). */
    fun setForeground(value: Boolean) { foreground.value = value }

    /** Fed by MainActivity on auth changes: the signed-in user id, or null on sign-out. */
    fun setAuthenticatedUser(userId: String?) { authUserId.value = userId }

    /** Idempotent — launches the three collectors once per process. */
    fun start() {
        if (!started.compareAndSet(false, true)) return

        // Build the pairing flow only when authenticated (the use case reads the user id
        // eagerly); fall back to "no couple" on any transient read error during a sign-out race.
        val coupleId = authUserId.flatMapLatest { uid ->
            if (uid == null) flowOf(null)
            else pairingStates()
                .map { (it as? PairingState.Paired)?.couple?.id }
                .catch { emit(null) }
        }.distinctUntilChanged()

        // 1) Subscribe only while foregrounded + paired; swap/teardown on any change.
        // connect/disconnect are wrapped so a Realtime failure (e.g. the realtime.messages
        // RLS policy not yet applied) degrades to "no bell" instead of killing this collector.
        // On every (re)subscribe we also fire a catch-up pullOnly so foregrounding the app
        // immediately fetches anything the partner changed while we were backgrounded — the
        // bell only covers changes that happen *while* both are live.
        scope.launch {
            combine(foreground, coupleId) { fg, cid -> if (fg) cid else null }
                .distinctUntilChanged()
                .collect { activeCoupleId ->
                    runCatching { bell.disconnect() }
                    if (activeCoupleId != null) {
                        runCatching { bell.connect(activeCoupleId) }
                            .onFailure { Log.w(TAG, "bell connect failed for couple:$activeCoupleId", it) }
                            .onSuccess { Log.d(TAG, "bell subscribed to couple:$activeCoupleId") }
                        runCatching { engine.pullOnly() } // catch-up on foreground/re-pair
                    }
                }
        }

        // 2) Partner ping → debounced pull-only.
        scope.launch {
            bell.pings
                .debounce(pullDebounceMs)
                .collect {
                    Log.d(TAG, "partner ping → pullOnly")
                    runCatching { engine.pullOnly() }
                }
        }

        // 3) Local write → debounced push-only → ring the bell iff rows actually went out.
        scope.launch {
            syncTrigger.signals
                .debounce(pushDebounceMs)
                .collect {
                    val sentRows = runCatching { engine.pushOnly() }.getOrDefault(false)
                    Log.d(TAG, "write → pushOnly sentRows=$sentRows")
                    if (sentRows) runCatching { bell.broadcast() }
                }
        }
    }

    private companion object {
        const val TAG = "LiveSync"
    }
}
