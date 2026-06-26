package com.iponlove.app.feature.user

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.user.data.UserRepositoryImpl
import com.iponlove.app.feature.user.data.local.UserDao
import com.iponlove.app.feature.user.data.local.UserEntity
import com.iponlove.app.feature.user.data.remote.UserDto
import com.iponlove.app.feature.user.data.remote.UserRemoteSource
import com.iponlove.app.feature.user.domain.usecase.EnsureCurrentUserRowUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class EnsureCurrentUserRowTest {

    private val dao = FakeUserDao()
    private val clock = SyncClock(now = { Instant.ofEpochMilli(99_000) })
    private val currentUser = CurrentUserProvider { "user-1" }

    @Test
    fun ensureLocalRow_rowAlreadyInRoom_noOp() = runTest {
        val existing = userEntity("user-1", coupleId = "couple-x", pendingSync = false)
        dao.store["user-1"] = existing
        val remote = FakeUserRemoteSource(fetchResult = null)
        val repo = UserRepositoryImpl(dao, clock, currentUser, remote)

        repo.ensureLocalRow("user-1", "Patty")

        // Existing row (and any name set earlier) is left untouched.
        assertThat(dao.store["user-1"]).isEqualTo(existing)
        assertThat(remote.fetchSelfCalled).isFalse()
    }

    @Test
    fun ensureLocalRow_reinstall_adoptsServerRowClean() = runTest {
        val serverRow = userDto("user-1", coupleId = "couple-x", serverRev = 42)
        val remote = FakeUserRemoteSource(fetchResult = serverRow)
        val repo = UserRepositoryImpl(dao, clock, currentUser, remote)

        repo.ensureLocalRow("user-1", "Patty")

        val saved = dao.store["user-1"]!!
        assertThat(saved.coupleId).isEqualTo("couple-x")
        assertThat(saved.pendingSync).isFalse()
        assertThat(saved.serverRev).isEqualTo(42)
    }

    @Test
    fun ensureLocalRow_genuineNewSignup_createsDirtyStub() = runTest {
        val remote = FakeUserRemoteSource(fetchResult = null)
        val repo = UserRepositoryImpl(dao, clock, currentUser, remote)

        repo.ensureLocalRow("user-1", null)

        val saved = dao.store["user-1"]!!
        assertThat(saved.coupleId).isNull()
        assertThat(saved.pendingSync).isTrue()
        assertThat(saved.serverRev).isNull()
    }

    @Test
    fun ensureLocalRow_newSignup_seedsDisplayNameOntoStub() = runTest {
        val remote = FakeUserRemoteSource(fetchResult = null)
        val repo = UserRepositoryImpl(dao, clock, currentUser, remote)

        repo.ensureLocalRow("user-1", "Patty")

        val saved = dao.store["user-1"]!!
        assertThat(saved.displayName).isEqualTo("Patty")
        assertThat(saved.pendingSync).isTrue()
    }

    @Test
    fun ensureLocalRow_remoteThrows_fallsBackToDirtyStub() = runTest {
        val remote = FakeUserRemoteSource(fetchResult = null, throws = true)
        val repo = UserRepositoryImpl(dao, clock, currentUser, remote)

        repo.ensureLocalRow("user-1", "Patty")

        val saved = dao.store["user-1"]!!
        assertThat(saved.displayName).isEqualTo("Patty")
        assertThat(saved.pendingSync).isTrue()
    }

    @Test
    fun useCase_threadsDisplayNameFromSessionMetadataOntoNewRow() = runTest {
        // The provider supplies the name captured at registration (auth metadata, ADR-0016);
        // the use case must seed it onto the freshly created row.
        val provider = object : CurrentUserProvider {
            override fun userId(): String = "user-1"
            override fun displayName(): String? = "Alvin"
        }
        val remote = FakeUserRemoteSource(fetchResult = null)
        val repo = UserRepositoryImpl(dao, clock, provider, remote)
        val useCase = EnsureCurrentUserRowUseCase(repo, provider)

        useCase()

        assertThat(dao.store["user-1"]!!.displayName).isEqualTo("Alvin")
    }
}

private class FakeUserDao : UserDao {
    val store = mutableMapOf<String, UserEntity>()
    override suspend fun upsert(entity: UserEntity) { store[entity.id] = entity }
    override suspend fun upsertAll(entities: List<UserEntity>) = entities.forEach { store[it.id] = it }
    override suspend fun getById(id: String): UserEntity? = store[id]
    override fun observeById(id: String): Flow<UserEntity?> = MutableStateFlow(store[id])
    override fun observePartner(coupleId: String, selfId: String): Flow<UserEntity?> = MutableStateFlow(null)
    override suspend fun dirtyRows(): List<UserEntity> = store.values.filter { it.pendingSync }
    override suspend fun clearPending(ids: List<String>) = ids.forEach { store[it]?.let { e -> store[it] = e.copy(pendingSync = false) } }
}

private class FakeUserRemoteSource(
    private val fetchResult: UserDto?,
    private val throws: Boolean = false,
) : UserRemoteSource {
    var fetchSelfCalled = false
    override suspend fun push(rows: List<UserDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<UserDto> = emptyList()
    override suspend fun fetchSelf(userId: String): UserDto? {
        fetchSelfCalled = true
        if (throws) throw RuntimeException("network error")
        return fetchResult
    }
}

private fun userEntity(
    id: String,
    coupleId: String? = null,
    pendingSync: Boolean = false,
    serverRev: Long? = null,
) = UserEntity(
    id = id,
    displayName = null,
    avatarUrl = null,
    accentColor = null,
    coupleId = coupleId,
    createdAt = Instant.ofEpochMilli(1_000),
    updatedAt = Instant.ofEpochMilli(1_000),
    isDeleted = false,
    serverRev = serverRev,
    pendingSync = pendingSync,
)

private fun userDto(
    id: String,
    coupleId: String? = null,
    serverRev: Long? = null,
) = UserDto(
    id = id,
    displayName = null,
    avatarUrl = null,
    accentColor = null,
    coupleId = coupleId,
    createdAt = Instant.ofEpochMilli(1_000),
    updatedAt = Instant.ofEpochMilli(1_000),
    serverRev = serverRev,
)
