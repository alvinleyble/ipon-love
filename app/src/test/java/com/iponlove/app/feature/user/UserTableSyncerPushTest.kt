package com.iponlove.app.feature.user

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.user.data.local.UserDao
import com.iponlove.app.feature.user.data.local.UserEntity
import com.iponlove.app.feature.user.data.remote.UserDto
import com.iponlove.app.feature.user.data.remote.UserEntitlementWrite
import com.iponlove.app.feature.user.data.remote.UserPushDto
import com.iponlove.app.feature.user.data.remote.UserRemoteSource
import com.iponlove.app.feature.user.data.sync.UserTableSyncer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * The users push is the one push in the app that is not a plain full-row upsert (ADR-0060):
 * profile columns go up as an upsert, entitlement goes through `set_self_entitlement`. Both
 * halves ride the SAME push so the existing dirty-flag outbox still provides offline retry —
 * no new marker column, per the prefer-existing-mechanism rule.
 */
class UserTableSyncerPushTest {

    private val ts = Instant.ofEpochMilli(1_000)

    private fun entity(
        id: String = "u1",
        isPremium: Boolean = false,
        entitlementSource: String = "NONE",
    ) = UserEntity(
        id = id,
        displayName = "Alvin",
        avatarUrl = null,
        accentColor = "#FF8AB0",
        coupleId = null,
        isPremium = isPremium,
        premiumUntil = null,
        entitlementSource = entitlementSource,
        entitlementCheckedAt = ts,
        createdAt = ts,
        updatedAt = ts,
        isDeleted = false,
        serverRev = null,
        pendingSync = true,
    )

    private class FakeDao(var dirty: List<UserEntity>) : UserDao {
        val cleared = mutableListOf<String>()
        override suspend fun upsert(entity: UserEntity) = Unit
        override suspend fun upsertAll(entities: List<UserEntity>) = Unit
        override suspend fun getById(id: String): UserEntity? = dirty.firstOrNull { it.id == id }
        override fun observeById(id: String): Flow<UserEntity?> = flowOf(null)
        override fun observePartner(coupleId: String, selfId: String): Flow<UserEntity?> =
            flowOf(null)
        override suspend fun dirtyRows(): List<UserEntity> = dirty
        override suspend fun clearPending(ids: List<String>) { cleared += ids }
    }

    private class FakeRemote(
        val pushFails: Boolean = false,
        val rpcFails: Boolean = false,
    ) : UserRemoteSource {
        val pushed = mutableListOf<UserPushDto>()
        val entitlementWrites = mutableListOf<UserEntitlementWrite>()

        override suspend fun push(rows: List<UserPushDto>): List<String> {
            if (pushFails) throw IOException("offline")
            pushed += rows
            return rows.map { it.id }
        }

        override suspend fun writeEntitlement(write: UserEntitlementWrite) {
            if (rpcFails) throw IOException("offline")
            entitlementWrites += write
        }

        override suspend fun pull(cursor: Long, limit: Int): List<UserDto> = emptyList()
        override suspend fun fetchSelf(userId: String): UserDto? = null
    }

    private class FakeCursors : SyncCursorStore {
        override suspend fun cursor(table: SyncTable): Long = 0
        override suspend fun setCursor(table: SyncTable, value: Long) = Unit
        override suspend fun reset() = Unit
    }

    private fun syncer(dao: UserDao, remote: UserRemoteSource) =
        UserTableSyncer(dao, remote, FakeCursors(), ConflictResolver())

    @Test
    fun push_sendsProfileUpsertAndEntitlementRpc_thenClearsPending() = runTest {
        val dao = FakeDao(listOf(entity(isPremium = true, entitlementSource = "PLAY")))
        val remote = FakeRemote()

        val pushed = syncer(dao, remote).push()

        assertThat(pushed).isTrue()
        assertThat(remote.pushed).hasSize(1)
        assertThat(remote.pushed.single().accentColor).isEqualTo("#FF8AB0")
        assertThat(remote.entitlementWrites).hasSize(1)
        assertThat(remote.entitlementWrites.single().source).isEqualTo("PLAY")
        assertThat(remote.entitlementWrites.single().isPremium).isTrue()
        // Acked → the outbox releases the row.
        assertThat(dao.cleared).containsExactly("u1")
    }

    /**
     * The offline-first guarantee of ADR-0060 §3. An entitlement write that can't reach the
     * server must leave the row dirty so the ordinary sync retries it — entitlement must not
     * become the one write in the app that is lost when offline.
     */
    @Test
    fun entitlementRpcFailure_leavesRowDirty_forRetry() = runTest {
        val dao = FakeDao(listOf(entity()))
        val remote = FakeRemote(rpcFails = true)

        val thrown = runCatching { syncer(dao, remote).push() }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IOException::class.java)
        // The profile half landed, but the row must NOT be released — the RPC still owes a write.
        assertThat(remote.pushed).hasSize(1)
        assertThat(dao.cleared).isEmpty()
    }

    /** Upsert first, RPC second: a genuine new signup's row must exist before the RPC looks
     *  for it, so a failed upsert must not leave a dangling entitlement call. */
    @Test
    fun upsertFailure_neverReachesTheRpc_leavesRowDirty() = runTest {
        val dao = FakeDao(listOf(entity()))
        val remote = FakeRemote(pushFails = true)

        val thrown = runCatching { syncer(dao, remote).push() }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IOException::class.java)
        assertThat(remote.entitlementWrites).isEmpty()
        assertThat(dao.cleared).isEmpty()
    }

    @Test
    fun noDirtyRows_pushesNothing() = runTest {
        val dao = FakeDao(emptyList())
        val remote = FakeRemote()

        assertThat(syncer(dao, remote).push()).isFalse()
        assertThat(remote.pushed).isEmpty()
        assertThat(remote.entitlementWrites).isEmpty()
    }
}
