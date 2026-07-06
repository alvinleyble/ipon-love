package com.iponlove.app.core.sync

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.couple.domain.model.Couple
import com.iponlove.app.feature.couple.domain.model.PairingState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The live-sync orchestration (ADR-0015): subscription lifecycle, bell→pull, and
 * write→push→bell — with the two guarantees that keep it from looping:
 *  - **no ping-pong:** a received ping pulls, and a pull never re-pushes or re-rings.
 *  - **no self-echo:** the writer's own broadcast never triggers its own pull.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoupleChannelManagerTest {

    /** A [CoupleBell] double: counts connect/disconnect/broadcast and emits pings on demand. */
    private class FakeCoupleBell : CoupleBell {
        val connectedTo = mutableListOf<String>()
        var disconnectCount = 0
        var broadcastCount = 0
        val currentChannel: String? get() = connectedTo.lastOrNull().takeIf { isConnected }
        private var isConnected = false

        private val _pings = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        override val pings: Flow<Unit> = _pings.asSharedFlow()

        override suspend fun connect(coupleId: String) {
            connectedTo += coupleId
            isConnected = true
        }
        override suspend fun disconnect() {
            disconnectCount++
            isConnected = false
        }
        override suspend fun broadcast() { broadcastCount++ }

        /** Simulate a partner ping arriving on the channel (own echoes excluded upstream). */
        suspend fun emitPartnerPing() = _pings.emit(Unit)
    }

    /** Records push/pull; push reports a configurable "sent rows" result. */
    private class RecordingSyncer(
        override val table: SyncTable,
        val log: MutableList<String>,
        private val pushSentRows: Boolean = false,
    ) : TableSyncer {
        override suspend fun push(): Boolean { log += "push"; return pushSentRows }
        override suspend fun pull() { log += "pull" }
    }

    private fun couple(id: String) =
        PairingState.Paired(
            couple = Couple(id, "Us", "ABC123", "u1", "u2", isDeleted = false),
            partner = null,
        )

    private fun managerWith(
        bell: FakeCoupleBell,
        engine: SyncEngine,
        trigger: SyncTrigger,
        pairing: Flow<PairingState>,
        scope: kotlinx.coroutines.CoroutineScope,
    ) = CoupleChannelManager(
        bell = bell,
        engine = engine,
        syncTrigger = trigger,
        pairingStates = { pairing },
        scope = scope,
        pushDebounceMs = 1_500,
        pullDebounceMs = 1_000,
    )

    @Test
    fun partnerPing_triggersPullOnly_afterDebounce_andRingsNoBell() = runTest {
        val bell = FakeCoupleBell()
        val log = mutableListOf<String>()
        val engine = SyncEngine(setOf(RecordingSyncer(SyncTable.USERS, log)))
        val manager = managerWith(bell, engine, SyncTrigger(), MutableStateFlow(couple("c1")), backgroundScope)
        manager.start()
        manager.setAuthenticatedUser("u1")
        manager.setForeground(true)
        runCurrent()
        log.clear() // drop the catch-up pull fired on subscribe; isolate the ping's effect

        bell.emitPartnerPing()
        advanceTimeBy(1_100)
        runCurrent()

        // A ping pulls — and only pulls. No push, so no broadcast: no ping-pong (ADR-0015).
        assertThat(log).containsExactly("pull")
        assertThat(bell.broadcastCount).isEqualTo(0)
    }

    @Test
    fun subscribe_firesCatchUpPull_soForegroundingFetchesMissedChanges() = runTest {
        val bell = FakeCoupleBell()
        val log = mutableListOf<String>()
        val engine = SyncEngine(setOf(RecordingSyncer(SyncTable.USERS, log)))
        val manager = managerWith(bell, engine, SyncTrigger(), MutableStateFlow(couple("c1")), backgroundScope)
        manager.start()
        manager.setAuthenticatedUser("u1")

        manager.setForeground(true)
        runCurrent()

        // Connecting the bell (foreground + paired) immediately pulls — covers changes the
        // partner made while we were backgrounded, which the bell alone would miss.
        assertThat(bell.connectedTo).containsExactly("c1")
        assertThat(log).containsExactly("pull")
    }

    @Test
    fun localWrite_triggersPushOnly_thenRingsBell_whenRowsSentAndConnected() = runTest {
        val bell = FakeCoupleBell()
        val log = mutableListOf<String>()
        val trigger = SyncTrigger()
        // Push reports rows actually went out → the bell should ring.
        val engine = SyncEngine(setOf(RecordingSyncer(SyncTable.USERS, log, pushSentRows = true)))
        val manager = managerWith(bell, engine, trigger, MutableStateFlow(couple("c1")), backgroundScope)
        manager.start()
        manager.setAuthenticatedUser("u1")
        manager.setForeground(true)
        runCurrent()
        log.clear() // drop the catch-up pull fired on subscribe

        trigger.requestPush()
        advanceTimeBy(1_600)
        runCurrent()

        assertThat(log).containsExactly("push")
        assertThat(bell.broadcastCount).isEqualTo(1)
    }

    @Test
    fun localWrite_pushesButDoesNotRingBell_whenNothingWasSent() = runTest {
        val bell = FakeCoupleBell()
        val log = mutableListOf<String>()
        val trigger = SyncTrigger()
        // Push reports nothing went out (e.g. already-synced rows) → no bell.
        val engine = SyncEngine(setOf(RecordingSyncer(SyncTable.USERS, log, pushSentRows = false)))
        val manager = managerWith(bell, engine, trigger, MutableStateFlow(couple("c1")), backgroundScope)
        manager.start()
        manager.setAuthenticatedUser("u1")
        manager.setForeground(true)
        runCurrent()
        log.clear() // drop the catch-up pull fired on subscribe

        trigger.requestPush()
        advanceTimeBy(1_600)
        runCurrent()

        assertThat(log).containsExactly("push")
        assertThat(bell.broadcastCount).isEqualTo(0)
    }

    @Test
    fun ownBroadcast_doesNotEchoBackIntoAPull() = runTest {
        val bell = FakeCoupleBell()
        val log = mutableListOf<String>()
        val trigger = SyncTrigger()
        val engine = SyncEngine(setOf(RecordingSyncer(SyncTable.USERS, log, pushSentRows = true)))
        val manager = managerWith(bell, engine, trigger, MutableStateFlow(couple("c1")), backgroundScope)
        manager.start()
        manager.setAuthenticatedUser("u1")
        manager.setForeground(true)
        runCurrent()
        log.clear() // drop the catch-up pull fired on subscribe

        // Write → push → broadcast. The bell broadcast must NOT feed our own pings flow
        // (receiveOwnBroadcasts = false), so no pull follows the broadcast.
        trigger.requestPush()
        advanceTimeBy(2_000)
        runCurrent()

        assertThat(bell.broadcastCount).isEqualTo(1)
        assertThat(log).containsExactly("push") // no trailing "pull" from a self-echo
    }

    @Test
    fun subscribes_onlyWhenForegroundedAndPaired() = runTest {
        val bell = FakeCoupleBell()
        val engine = SyncEngine(emptySet())
        val pairing = MutableStateFlow<PairingState>(PairingState.NotPaired)
        val manager = managerWith(bell, engine, SyncTrigger(), pairing, backgroundScope)
        manager.start()
        manager.setAuthenticatedUser("u1")

        // Paired but backgrounded → no subscription.
        pairing.value = couple("c1")
        runCurrent()
        assertThat(bell.connectedTo).isEmpty()

        // Foregrounded + paired → subscribe.
        manager.setForeground(true)
        runCurrent()
        assertThat(bell.connectedTo).containsExactly("c1")
        assertThat(bell.currentChannel).isEqualTo("c1")
    }

    @Test
    fun unsubscribes_whenBackgrounded() = runTest {
        val bell = FakeCoupleBell()
        val manager = managerWith(bell, SyncEngine(emptySet()), SyncTrigger(), MutableStateFlow(couple("c1")), backgroundScope)
        manager.start()
        manager.setAuthenticatedUser("u1")
        manager.setForeground(true)
        runCurrent()
        assertThat(bell.connectedTo).containsExactly("c1")

        manager.setForeground(false)
        runCurrent()
        assertThat(bell.currentChannel).isNull()
    }

    @Test
    fun swapsChannel_onRepair() = runTest {
        val bell = FakeCoupleBell()
        val pairing = MutableStateFlow(couple("c1"))
        val manager = managerWith(bell, SyncEngine(emptySet()), SyncTrigger(), pairing, backgroundScope)
        manager.start()
        manager.setAuthenticatedUser("u1")
        manager.setForeground(true)
        runCurrent()

        pairing.value = couple("c2")
        runCurrent()

        // Old channel torn down, new one connected.
        assertThat(bell.connectedTo).containsExactly("c1", "c2").inOrder()
        assertThat(bell.currentChannel).isEqualTo("c2")
    }

    @Test
    fun localWrite_doesNotPush_afterSignOut() = runTest {
        val bell = FakeCoupleBell()
        val log = mutableListOf<String>()
        val trigger = SyncTrigger()
        val engine = SyncEngine(setOf(RecordingSyncer(SyncTable.USERS, log, pushSentRows = true)))
        val manager = managerWith(bell, engine, trigger, MutableStateFlow(couple("c1")), backgroundScope)
        manager.start()
        manager.setAuthenticatedUser("u1")
        manager.setForeground(true)
        runCurrent()
        log.clear() // drop the catch-up pull fired on subscribe

        // Write lands just before sign-out; the debounced push must not fire against a
        // torn-down session (the LiveSyncScope outlives it).
        trigger.requestPush()
        manager.setAuthenticatedUser(null)
        advanceTimeBy(1_600)
        runCurrent()

        assertThat(log).isEmpty()
        assertThat(bell.broadcastCount).isEqualTo(0)
    }

    @Test
    fun neverSubscribes_whenUnauthenticated_evenIfForegrounded() = runTest {
        val bell = FakeCoupleBell()
        // Pairing flow would throw if built pre-auth; gating must keep it unbuilt.
        val manager = CoupleChannelManager(
            bell = bell,
            engine = SyncEngine(emptySet()),
            syncTrigger = SyncTrigger(),
            pairingStates = { error("pairing flow must not be built before sign-in") },
            scope = backgroundScope,
        )
        manager.start()
        manager.setForeground(true)
        runCurrent()

        assertThat(bell.connectedTo).isEmpty()
    }
}
