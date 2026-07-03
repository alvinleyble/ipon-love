package com.iponlove.app.core.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.data.ClockOffsetStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun testDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { tmpFolder.newFile("test_sync_prefs.preferences_pb") }

    private class RecordingSyncer(
        override val table: SyncTable,
        private val log: MutableList<String>,
    ) : TableSyncer {
        override suspend fun push(): Boolean { log += "push:${table.name}"; return false }
        override suspend fun pull() { log += "pull:${table.name}" }
    }

    @Test
    fun sync_pushIsSequentialFkOrder_pullRunsAllTables() = runTest {
        val log = mutableListOf<String>()
        // Contributed out of FK order on purpose; engine must reorder push.
        val syncers = setOf(
            RecordingSyncer(SyncTable.NOTE_IMAGES, log),
            RecordingSyncer(SyncTable.ACCOUNTS, log),
            RecordingSyncer(SyncTable.USERS, log),
        )

        SyncEngine(syncers).sync()

        // Push must be strictly FK-ordered (parent before child).
        val pushEntries = log.filter { it.startsWith("push:") }
        assertThat(pushEntries).containsExactly(
            "push:USERS", "push:ACCOUNTS", "push:NOTE_IMAGES",
        ).inOrder()

        // Pull fires all tables concurrently — every table pulled, order is not guaranteed.
        val pullEntries = log.filter { it.startsWith("pull:") }
        assertThat(pullEntries).containsExactlyElementsIn(
            listOf("pull:USERS", "pull:ACCOUNTS", "pull:NOTE_IMAGES"),
        )
    }

    @Test
    fun sync_pushFailureIsIsolated_stillPushesOthersAndPulls_thenRethrows() = runTest {
        // F1: a table whose push is RLS-rejected must NOT abort the run before the pull phase —
        // otherwise the client never learns why (e.g. a goal was unshared) and the poisoned row
        // wedges every future sync. The failure is remembered and rethrown after pull.
        val log = mutableListOf<String>()
        val poison = object : TableSyncer {
            override val table = SyncTable.GOAL_CONTRIBUTIONS
            override suspend fun push(): Boolean {
                log += "push:GOAL_CONTRIBUTIONS"
                throw IllegalStateException("rls-reject")
            }
            override suspend fun pull() { log += "pull:GOAL_CONTRIBUTIONS" }
        }
        val engine = SyncEngine(
            setOf(RecordingSyncer(SyncTable.USERS, log), poison, RecordingSyncer(SyncTable.NOTES, log)),
        )

        val result = runCatching { engine.sync() }

        assertThat(result.isFailure).isTrue()
        assertThat(engine.state.value).isEqualTo(SyncState.Error("rls-reject"))
        // Every table still got its push attempt, and every table still pulled.
        assertThat(log).containsAtLeast("push:USERS", "push:GOAL_CONTRIBUTIONS", "push:NOTES")
        assertThat(log.filter { it.startsWith("pull:") }).containsExactlyElementsIn(
            listOf("pull:USERS", "pull:GOAL_CONTRIBUTIONS", "pull:NOTES"),
        )
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
            override suspend fun push() = false
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
                override suspend fun push() = false
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
                override suspend fun push() = false
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

    @Test
    fun sync_calibratesClock_onSuccess() = runTest {
        val clock = SyncClock()
        val store = ClockOffsetStore(testDataStore())
        // Simulate server 5 seconds ahead of local.
        val fakeServerNow = Instant.now().plusSeconds(5)

        val engine = SyncEngine(
            syncers = emptySet(),
            clock = clock,
            clockOffsetStore = store,
            serverTimeFetcher = { fakeServerNow },
        )
        engine.sync()

        // Offset should be approximately +5000ms (give or take a few ms for execution time).
        assertThat(clock.offsetMillis).isGreaterThan(4_900L)
        assertThat(clock.offsetMillis).isLessThan(5_100L)
    }

    @Test
    fun sync_doesNotCalibrateClock_onFailure() = runTest {
        val clock = SyncClock(initialOffsetMillis = 0L)
        val store = ClockOffsetStore(testDataStore())

        val engine = SyncEngine(
            syncers = setOf(object : TableSyncer {
                override val table = SyncTable.USERS
                override suspend fun push() = false
                override suspend fun pull(): Unit = throw IllegalStateException("network")
            }),
            clock = clock,
            clockOffsetStore = store,
            serverTimeFetcher = { Instant.now().plusSeconds(10) },
        )

        runCatching { engine.sync() }

        assertThat(clock.offsetMillis).isEqualTo(0L)
    }
}
