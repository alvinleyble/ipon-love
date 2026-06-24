package com.iponlove.app.core.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

    private class RecordingSyncer(
        override val table: SyncTable,
        private val log: MutableList<String>,
    ) : TableSyncer {
        override suspend fun push() { log += "push:${table.name}" }
        override suspend fun pull() { log += "pull:${table.name}" }
    }

    @Test
    fun sync_runsSyncersInFkOrder_pushAllThenPullAll() = runTest {
        val log = mutableListOf<String>()
        // Contributed out of FK order on purpose; engine must reorder.
        val syncers = setOf(
            RecordingSyncer(SyncTable.NOTE_IMAGES, log),
            RecordingSyncer(SyncTable.ACCOUNTS, log),
            RecordingSyncer(SyncTable.USERS, log),
        )

        SyncEngine(syncers).sync()

        assertThat(log).containsExactly(
            "push:USERS", "push:ACCOUNTS", "push:NOTE_IMAGES",
            "pull:USERS", "pull:ACCOUNTS", "pull:NOTE_IMAGES",
        ).inOrder()
    }

    @Test
    fun sync_emptySyncers_isNoOpSuccess() = runTest {
        val engine = SyncEngine(emptySet())

        assertThat(engine.sync()).isTrue()
        assertThat(engine.state.value).isInstanceOf(SyncState.Success::class.java)
    }

    @Test
    fun sync_coalescesConcurrentTriggers_singleFlight() = runTest {
        val gate = CompletableDeferred<Unit>()
        val runs = AtomicInteger(0)
        val gated = object : TableSyncer {
            override val table = SyncTable.USERS
            override suspend fun push() = Unit
            override suspend fun pull() {
                runs.incrementAndGet()
                gate.await()
            }
        }
        val engine = SyncEngine(setOf(gated))

        val first = launch { engine.sync() }
        runCurrent() // let the first run start and park at the gate

        assertThat(engine.state.value).isEqualTo(SyncState.Syncing)
        // A second trigger while in flight must not start a second pass.
        assertThat(engine.sync()).isFalse()
        assertThat(runs.get()).isEqualTo(1)

        gate.complete(Unit)
        first.join()

        assertThat(runs.get()).isEqualTo(1)
        assertThat(engine.state.value).isInstanceOf(SyncState.Success::class.java)
    }

    @Test
    fun sync_surfacesError_andRethrows() = runTest {
        val engine = SyncEngine(
            setOf(object : TableSyncer {
                override val table = SyncTable.USERS
                override suspend fun push() = Unit
                override suspend fun pull(): Unit = throw IllegalStateException("boom")
            }),
        )

        val result = runCatching { engine.sync() }

        assertThat(result.isFailure).isTrue()
        assertThat(engine.state.value).isEqualTo(SyncState.Error("boom"))
    }

    @Test
    fun sync_recoversToSuccess_afterAPriorError() = runTest {
        var shouldFail = true
        val engine = SyncEngine(
            setOf(object : TableSyncer {
                override val table = SyncTable.USERS
                override suspend fun push() = Unit
                override suspend fun pull() {
                    if (shouldFail) throw IllegalStateException("boom")
                }
            }),
        )

        runCatching { engine.sync() }
        shouldFail = false
        engine.sync()

        assertThat(engine.state.value).isInstanceOf(SyncState.Success::class.java)
    }
}
