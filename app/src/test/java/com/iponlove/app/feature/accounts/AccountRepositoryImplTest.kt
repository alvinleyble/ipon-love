package com.iponlove.app.feature.accounts

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.accounts.data.AccountRepositoryImpl
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.model.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * The repository is the single enforcement point for the sync write rules
 * (ADR-0001 stamping, ADR-0002 dirty flag, ADR-0010 soft delete).
 */
class AccountRepositoryImplTest {

    private val dao = FakeAccountDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val repository = AccountRepositoryImpl(dao, clock, currentUser)

    private fun newAccount(id: String, name: String = "GCash") = Account(
        id = id,
        name = name,
        type = AccountType.EWALLET,
        openingBalance = BigDecimal("500.00"),
    )

    @Test
    fun upsert_newAccount_stampsOwnerAndSyncColumns() = runTest {
        repository.upsertAccount(newAccount("a"))

        val row = dao.store.getValue("a")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.pendingSync).isTrue()
        assertThat(row.isDeleted).isFalse()
        assertThat(row.serverRev).isNull()
        assertThat(row.updatedAt).isEqualTo(now)
        assertThat(row.createdAt).isEqualTo(now)
    }

    @Test
    fun upsert_existingAccount_advancesUpdatedAtMonotonically_andPreservesProvenance() = runTest {
        // Seed a server-synced row directly in the dao.
        dao.store["a"] = accountEntity(
            id = "a",
            userId = "owner-x",
            createdAt = Instant.ofEpochMilli(1_000),
            updatedAt = Instant.ofEpochMilli(10_000),
            serverRev = 55,
            pendingSync = false,
        )
        // Wall clock has not advanced past the existing stamp.
        now = Instant.ofEpochMilli(10_000)

        repository.upsertAccount(newAccount("a", name = "GCash Renamed"))

        val row = dao.store.getValue("a")
        assertThat(row.name).isEqualTo("GCash Renamed")
        assertThat(row.pendingSync).isTrue()
        // Monotonic: max(now, prev + 1ms) = prev + 1ms.
        assertThat(row.updatedAt).isEqualTo(Instant.ofEpochMilli(10_001))
        // Provenance survives an edit.
        assertThat(row.userId).isEqualTo("owner-x")
        assertThat(row.createdAt).isEqualTo(Instant.ofEpochMilli(1_000))
        assertThat(row.serverRev).isEqualTo(55)
    }

    @Test
    fun setArchived_flagsAndMarksDirty() = runTest {
        dao.store["a"] = accountEntity(id = "a", updatedAt = Instant.ofEpochMilli(1_000))

        repository.setArchived("a", archived = true)

        val row = dao.store.getValue("a")
        assertThat(row.isArchived).isTrue()
        assertThat(row.pendingSync).isTrue()
        assertThat(row.updatedAt).isEqualTo(now)
    }

    @Test
    fun delete_isSoft_setsTombstoneAndMarksDirty() = runTest {
        dao.store["a"] = accountEntity(id = "a", serverRev = 3, updatedAt = Instant.ofEpochMilli(1_000))

        repository.deleteAccount("a")

        val row = dao.store.getValue("a")
        assertThat(row.isDeleted).isTrue()
        assertThat(row.pendingSync).isTrue()
        // Tombstone keeps its identity so the delete can sync (ADR-0010).
        assertThat(row.serverRev).isEqualTo(3)
    }

    @Test
    fun observeAccounts_hidesDeleted_andMapsToDomain() = runTest {
        dao.store["a"] = accountEntity(id = "a", name = "Cash", position = 0)
        dao.store["b"] = accountEntity(id = "b", name = "Gone", position = 1, isDeleted = true)

        val accounts = repository.observeAccounts().first()

        assertThat(accounts.map { it.name }).containsExactly("Cash")
    }
}
