package com.iponlove.app.core.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The narrow [SyncEngine.pushOnly] / [SyncEngine.pullOnly] entry points (ADR-0015): each runs
 * exactly one half of a sync, in FK order, and coalesces with any in-flight run via the shared
 * single-flight mutex.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEnginePushPullTest {

    /** Records the order of push/pull calls; push reports a configurable "sent rows" result. */
    private class RecordingSyncer(
        override val table: SyncTable,
        private val log: MutableList<String>,
        private val pushSentRows: Boolean = false,
    ) : TableSyncer {
        override suspend fun push(): Boolean {
            log += "push:${table.name}"
            return pushSentRows
        }
        override suspend fun pull() { log += "pull:${table.name}" }
    }

    @Test
    fun pushOnly_runsPushHalfInFkOrder_andNeverPulls() = runTest {
        val log = mutableListOf<String>()
        val syncers = setOf(
            RecordingSyncer(SyncTable.NOTE_IMAGES, log),
            RecordingSyncer(SyncTable.ACCOUNTS, log),
            RecordingSyncer(SyncTable.USERS, log),
        )

        SyncEngine(syncers).pushOnly()

        assertThat(log).containsExactly(
            "push:USERS", "push:ACCOUNTS", "push:NOTE_IMAGES",
        ).inOrder()
    }

    @Test
    fun pushOnly_runsPreSyncStepsBeforePush() = runTest {
        val log = mutableListOf<String>()
        val uploader = object : PreSyncStep {
            override suspend fun run() { log += "presync" }
        }

        SyncEngine(
            syncers = setOf(RecordingSyncer(SyncTable.NOTE_IMAGES, log)),
            preSyncSteps = setOf(uploader),
        ).pushOnly()

        assertThat(log).containsExactly("presync", "push:NOTE_IMAGES").inOrder()
    }

    @Test
    fun pushOnly_returnsTrue_whenAnySyncerSentRows() = runTest {
        val log = mutableListOf<String>()
        val engine = SyncEngine(
            setOf(
                RecordingSyncer(SyncTable.USERS, log, pushSentRows = false),
                RecordingSyncer(SyncTable.ACCOUNTS, log, pushSentRows = true),
            ),
        )

        assertThat(engine.pushOnly()).isTrue()
    }

    @Test
    fun pushOnly_returnsFalse_whenNothingWasSent() = runTest {
        val log = mutableListOf<String>()
        val engine = SyncEngine(
            setOf(
                RecordingSyncer(SyncTable.USERS, log, pushSentRows = false),
                RecordingSyncer(SyncTable.ACCOUNTS, log, pushSentRows = false),
            ),
        )

        assertThat(engine.pushOnly()).isFalse()
    }

    @Test
    fun pushOnly_doesNotTouchSyncState() = runTest {
        val engine = SyncEngine(setOf(RecordingSyncer(SyncTable.USERS, mutableListOf())))

        engine.pushOnly()

        // Silent background micro-sync: the visible spinner state never flips.
        assertThat(engine.state.value).isEqualTo(SyncState.Idle)
    }

    @Test
    fun pullOnly_pullsAllTables_andNeverPushes() = runTest {
        val log = mutableListOf<String>()
        val syncers = setOf(
            RecordingSyncer(SyncTable.NOTE_IMAGES, log),
            RecordingSyncer(SyncTable.ACCOUNTS, log),
            RecordingSyncer(SyncTable.USERS, log),
        )

        SyncEngine(syncers).pullOnly()

        // Pulls fire concurrently — every table pulled, no pushes.
        assertThat(log).containsNoneOf("push:USERS", "push:ACCOUNTS", "push:NOTE_IMAGES")
        assertThat(log).containsExactlyElementsIn(
            listOf("pull:USERS", "pull:ACCOUNTS", "pull:NOTE_IMAGES"),
        )
    }

    @Test
    fun pushOnly_coalescesWithInFlightSync_returnsFalse_andDoesNotPush() = runTest {
        val gate = CompletableDeferred<Unit>()
        val log = mutableListOf<String>()
        val gated = object : TableSyncer {
            override val table = SyncTable.USERS
            override suspend fun push() = false
            override suspend fun pull() {
                log += "pull:sync"
                gate.await()
            }
        }
        val engine = SyncEngine(setOf(gated))

        val first = launch { engine.sync() }
        runCurrent() // first sync parks at the gate, holding the single-flight lock

        // pushOnly can't acquire the lock → no-op, returns false (so no bell is rung).
        assertThat(engine.pushOnly()).isFalse()

        gate.complete(Unit)
        first.join()
        assertThat(log).containsExactly("pull:sync")
    }

    @Test
    fun pullOnly_coalescesWithInFlightSync_returnsFalse() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gated = object : TableSyncer {
            override val table = SyncTable.USERS
            override suspend fun push() = false
            override suspend fun pull() { gate.await() }
        }
        val engine = SyncEngine(setOf(gated))

        val first = launch { engine.sync() }
        runCurrent()

        assertThat(engine.pullOnly()).isFalse()

        gate.complete(Unit)
        first.join()
    }
}
